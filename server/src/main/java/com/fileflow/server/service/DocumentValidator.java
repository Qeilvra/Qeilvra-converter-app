package com.fileflow.server.service;

import com.fileflow.server.config.ProcessingProperties;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class DocumentValidator {
    public enum Kind { PDF, DOCX }
    private static final long MAX_EXPANDED_DOCX_BYTES = 200L * 1024 * 1024;
    private final ProcessingProperties properties;

    public DocumentValidator(ProcessingProperties properties) { this.properties = properties; }

    public byte[] require(MultipartFile file, Kind kind) {
        if (file == null || file.isEmpty()) throw new ProcessingException("Choose a non-empty file.");
        if (file.getSize() > properties.maxFileBytes()) throw new ProcessingException("The file exceeds the 50 MB processing limit.");
        try {
            byte[] bytes = file.getBytes();
            if (kind == Kind.PDF) validatePdf(bytes); else validateDocx(bytes);
            return bytes;
        } catch (IOException error) {
            throw new ProcessingException("The uploaded file could not be read.", error);
        }
    }

    private void validatePdf(byte[] bytes) {
        if (bytes.length < 8 || bytes[0] != '%' || bytes[1] != 'P' || bytes[2] != 'D' || bytes[3] != 'F' || bytes[4] != '-') {
            throw new ProcessingException("The uploaded file is not a valid PDF.");
        }
        try (PDDocument document = PDDocument.load(new ByteArrayInputStream(bytes), MemoryUsageSetting.setupTempFileOnly())) {
            if (document.isEncrypted()) throw new ProcessingException("Password-protected PDFs are not supported.");
            if (document.getNumberOfPages() == 0) throw new ProcessingException("The PDF has no pages.");
            if (document.getNumberOfPages() > properties.maxPages()) {
                throw new ProcessingException("This PDF exceeds the " + properties.maxPages() + "-page processing limit.");
            }
        } catch (ProcessingException error) {
            throw error;
        } catch (IOException error) {
            throw new ProcessingException("The uploaded file is not a readable PDF.", error);
        }
    }

    private static void validateDocx(byte[] bytes) throws IOException {
        if (bytes.length < 4 || bytes[0] != 'P' || bytes[1] != 'K') throw new ProcessingException("The uploaded file is not a valid DOCX package.");
        long expanded = 0;
        int entries = 0;
        boolean documentXml = false;
        byte[] buffer = new byte[8192];
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > 10_000) throw new ProcessingException("The DOCX package contains too many entries.");
                if ("word/document.xml".equals(entry.getName())) documentXml = true;
                int read;
                while ((read = zip.read(buffer)) != -1) {
                    expanded += read;
                    if (expanded > MAX_EXPANDED_DOCX_BYTES) throw new ProcessingException("The DOCX package expands beyond the safe limit.");
                }
            }
        }
        if (!documentXml) throw new ProcessingException("The DOCX package has no main document.");
    }
}
