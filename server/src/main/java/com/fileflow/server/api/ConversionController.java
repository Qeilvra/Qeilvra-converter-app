package com.fileflow.server.api;

import com.fileflow.server.config.ProcessingProperties;
import com.fileflow.server.service.DocumentValidator;
import com.fileflow.server.service.OfficeConversionService;
import com.fileflow.server.service.PdfCompressionService;
import com.fileflow.server.service.TableExtractionService;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
public class ConversionController {
    private static final MediaType DOCX = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    private static final MediaType XLSX = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    private final DocumentValidator validator;
    private final OfficeConversionService office;
    private final PdfCompressionService compression;
    private final TableExtractionService tables;
    private final ProcessingProperties properties;

    public ConversionController(DocumentValidator validator, OfficeConversionService office, PdfCompressionService compression,
                                TableExtractionService tables, ProcessingProperties properties) {
        this.validator = validator;
        this.office = office;
        this.compression = compression;
        this.tables = tables;
        this.properties = properties;
    }

    @GetMapping("/health/capabilities")
    public Map<String, Object> capabilities() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "ready");
        result.put("maxFileBytes", properties.maxFileBytes());
        result.put("maxPages", properties.maxPages());
        result.put("operations", java.util.List.of("pdf-to-docx", "docx-to-pdf", "pdf-compress", "pdf-tables"));
        result.put("retention", "temporary-workspace-deleted-after-request");
        return result;
    }

    @PostMapping(path = "/convert/pdf-to-docx", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> pdfToDocx(@RequestParam MultipartFile file) {
        byte[] input = validator.require(file, DocumentValidator.Kind.PDF);
        return download(office.pdfToDocx(input), DOCX, baseName(file) + ".docx", input.length, null);
    }

    @PostMapping(path = "/convert/docx-to-pdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> docxToPdf(@RequestParam MultipartFile file) {
        byte[] input = validator.require(file, DocumentValidator.Kind.DOCX);
        return download(office.docxToPdf(input), MediaType.APPLICATION_PDF, baseName(file) + ".pdf", input.length, null);
    }

    @PostMapping(path = "/pdf/compress", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> compress(@RequestParam MultipartFile file,
                                           @RequestParam(defaultValue = "BALANCED") PdfCompressionService.Level level) {
        byte[] input = validator.require(file, DocumentValidator.Kind.PDF);
        PdfCompressionService.Result result = compression.compress(input, level);
        return download(result.bytes(), MediaType.APPLICATION_PDF, baseName(file) + "-compressed.pdf", input.length,
                Map.of("X-FileFlow-Reduced", String.valueOf(result.reduced()), "X-FileFlow-Images-Optimized", String.valueOf(result.imagesOptimized())));
    }

    @PostMapping(path = "/pdf/tables", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public TableExtractionService.Response extractTables(@RequestParam MultipartFile file) {
        return tables.extract(validator.require(file, DocumentValidator.Kind.PDF));
    }

    @PostMapping(path = "/pdf/tables/xlsx", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<byte[]> tablesToXlsx(@RequestBody TableExtractionService.Response request) {
        byte[] output = tables.toXlsx(request.tables());
        return download(output, XLSX, "fileflow-tables.xlsx", 0, null);
    }

    private static ResponseEntity<byte[]> download(byte[] bytes, MediaType type, String filename, long originalSize, Map<String, String> extraHeaders) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(type);
        headers.setContentLength(bytes.length);
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build());
        headers.set("X-FileFlow-Original-Size", String.valueOf(originalSize));
        headers.set("X-FileFlow-Output-Size", String.valueOf(bytes.length));
        if (extraHeaders != null) extraHeaders.forEach(headers::set);
        return ResponseEntity.ok().headers(headers).body(bytes);
    }

    private static String baseName(MultipartFile file) {
        String name = file.getOriginalFilename() == null ? "fileflow-output" : file.getOriginalFilename();
        String value = name.replaceFirst("\\.[^.]+$", "").replaceAll("[^a-zA-Z0-9._-]+", "-").replaceAll("^-+|-+$", "");
        return value.isBlank() ? "fileflow-output" : value.substring(0, Math.min(100, value.length()));
    }
}
