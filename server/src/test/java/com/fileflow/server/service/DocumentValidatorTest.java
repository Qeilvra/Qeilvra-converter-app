package com.fileflow.server.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import com.fileflow.server.config.ProcessingProperties;
import java.time.Duration;
import java.io.ByteArrayOutputStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class DocumentValidatorTest {
    private final DocumentValidator validator = new DocumentValidator(new ProcessingProperties(
            1024, 10, Duration.ofSeconds(1), 1, "http://localhost", "soffice", "qpdf", "pdf2docx {input} {output}"));

    @Test
    void acceptsPdfMagicBytes() throws Exception {
        byte[] pdf;
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.addPage(new org.apache.pdfbox.pdmodel.PDPage());
            document.save(output);
            pdf = output.toByteArray();
        }
        assertArrayEquals(pdf, validator.require(new MockMultipartFile("file", "a.pdf", "application/pdf", pdf), DocumentValidator.Kind.PDF));
    }

    @Test
    void rejectsExtensionOnlyPdf() {
        assertThrows(ProcessingException.class, () -> validator.require(
                new MockMultipartFile("file", "a.pdf", "application/pdf", "not pdf".getBytes()), DocumentValidator.Kind.PDF));
    }
}
