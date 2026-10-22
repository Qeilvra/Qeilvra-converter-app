package com.fileflow.server.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
    "fileflow.processing.libre-office-command=fileflow-missing-office-engine",
    "fileflow.processing.qpdf-command=fileflow-missing-qpdf-engine",
    "fileflow.processing.pdf-to-docx-command=fileflow-missing-word-engine {input} {output}"
})
@AutoConfigureMockMvc
class ApiWorkflowTest {
    @Autowired MockMvc mvc;

    @Test void healthReportsMissingEnginesButAvailableJavaTools() throws Exception {
        mvc.perform(get("/api/v1/health/capabilities")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("degraded"))
                .andExpect(jsonPath("$.dependencies.pdf-to-docx").value(false))
                .andExpect(jsonPath("$.operations[0]").value("pdf-compress"))
                .andExpect(header().string("Cache-Control", "no-store, max-age=0"));
    }

    @Test void spreadsheetEndpointPreservesIdentifiersInReopenedWorkbook() throws Exception {
        String payload = """
                {"tables":[{"id":"a","page":1,"rows":[[{"value":"001234","uncertain":false},{"value":"12345678901234567890","uncertain":false}]]}],"pageCount":1,"scanned":false}
                """;
        byte[] output = mvc.perform(post("/api/v1/pdf/tables/xlsx").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isOk()).andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andReturn().getResponse().getContentAsByteArray();
        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(output))) {
            assertEquals("001234", workbook.getSheetAt(0).getRow(0).getCell(0).getStringCellValue());
            assertEquals("12345678901234567890", workbook.getSheetAt(0).getRow(0).getCell(1).getStringCellValue());
        }
    }

    @Test void invalidRequestsReturnActionableClientStatus() throws Exception {
        mvc.perform(multipart("/api/v1/pdf/tables")).andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/v1/pdf/tables").file(new MockMultipartFile("file", "fake.pdf", "application/pdf", "fake".getBytes())))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/api/v1/pdf/tables/xlsx").contentType(MediaType.APPLICATION_JSON).content("{bad"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/pdf/tables/xlsx").contentType(MediaType.APPLICATION_JSON).content("{\"tables\":[null]}"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(multipart("/api/v1/pdf/compress").file(new MockMultipartFile("file", pdf())).param("level", "UNKNOWN"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/nonexistent")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/pdf/tables")).andExpect(status().isMethodNotAllowed());
    }

    @Test void scanIsFlaggedAndNeverReturnedAsSuccessfulEditableWord() throws Exception {
        mvc.perform(multipart("/api/v1/pdf/tables").file(new MockMultipartFile("file", pdf())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.scanned").value(true)).andExpect(jsonPath("$.tables").isEmpty());
        mvc.perform(multipart("/api/v1/convert/pdf-to-docx").file(new MockMultipartFile("file", pdf())))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("OCR")));
    }

    @Test void missingOfficeIsUnavailableAndCompressionNeverClaimsUnchangedWasReduced() throws Exception {
        byte[] docx;
        try (var document = new XWPFDocument(); var output = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("A real document"); document.write(output); docx = output.toByteArray();
        }
        mvc.perform(multipart("/api/v1/convert/docx-to-pdf").file(new MockMultipartFile("file", "sample.docx", "application/octet-stream", docx)))
                .andExpect(status().isServiceUnavailable());
        byte[] source = pdf();
        var response = mvc.perform(multipart("/api/v1/pdf/compress").file(new MockMultipartFile("file", source)))
                .andExpect(status().isOk()).andReturn().getResponse();
        byte[] result = response.getContentAsByteArray();
        assertEquals(Boolean.toString(result.length < source.length), response.getHeader("X-FileFlow-Reduced"));
        try (var document = PDDocument.load(result)) { assertEquals(1, document.getNumberOfPages()); }
    }

    @Test void corsAllowsConfiguredOriginAndRejectsUntrustedOrigin() throws Exception {
        mvc.perform(options("/api/v1/pdf/tables").header("Origin", "http://localhost:5173").header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
        mvc.perform(options("/api/v1/pdf/tables").header("Origin", "https://attacker.invalid").header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }

    private static byte[] pdf() throws Exception {
        try (var document = new PDDocument(); var output = new ByteArrayOutputStream()) {
            document.addPage(new PDPage()); document.save(output); return output.toByteArray();
        }
    }
}
