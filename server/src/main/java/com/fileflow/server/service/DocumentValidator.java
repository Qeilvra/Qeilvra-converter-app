package com.fileflow.server.service;

import com.fileflow.server.config.ProcessingProperties;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.HashSet;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.openxml4j.opc.TargetMode;
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
        if (file.getSize() > properties.maxFileBytes()) throw new ProcessingException(413, "The file exceeds the configured processing size limit.");
        try {
            byte[] bytes = file.getBytes();
            if (bytes.length > properties.maxFileBytes()) throw new ProcessingException(413, "The file exceeds the configured processing size limit.");
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
        var names = new HashSet<String>();
        byte[] buffer = new byte[8192];
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > 10_000) throw new ProcessingException("The DOCX package contains too many entries.");
                String name = entry.getName();
                if (!names.add(name) || name.startsWith("/") || name.contains("\\") || java.util.Arrays.asList(name.split("/")).contains(".."))
                    throw new ProcessingException("The DOCX package contains unsafe or duplicate entry paths.");
                if ("word/document.xml".equals(entry.getName())) documentXml = true;
                int read;
                while ((read = zip.read(buffer)) != -1) {
                    expanded += read;
                    if (expanded > MAX_EXPANDED_DOCX_BYTES) throw new ProcessingException("The DOCX package expands beyond the safe limit.");
                }
            }
        }
        if (!documentXml) throw new ProcessingException("The DOCX package has no main document.");
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            if (document.getDocument().getBody() == null) throw new ProcessingException("The DOCX package has no document body.");
            if (!"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml".equals(document.getPackagePart().getContentType()))
                throw new ProcessingException("Only standard DOCX documents are supported. Save macro-enabled documents or templates as DOCX first.");
            for (var part : document.getPackage().getParts()) {
                if (part.getContentType().toLowerCase(java.util.Locale.ROOT).contains("vbaproject"))
                    throw new ProcessingException("DOCX packages containing macros are not supported. Remove macros and try again.");
                if (part.isRelationshipPart()) continue;
                for (var relationship : part.getRelationships()) {
                    if (relationship.getTargetMode() == TargetMode.EXTERNAL) {
                        String scheme = relationship.getTargetURI().getScheme();
                        boolean safeHyperlink = relationship.getRelationshipType().endsWith("/hyperlink") && scheme != null
                                && java.util.Set.of("http", "https", "mailto").contains(scheme.toLowerCase(java.util.Locale.ROOT));
                        if (!safeHyperlink) throw new ProcessingException("DOCX files with external linked resources or unsafe links are not supported. Embed the resources and use HTTP, HTTPS, or mailto links.");
                    }
                }
            }
        } catch (ProcessingException error) { throw error; }
        catch (Exception error) { throw new ProcessingException("The uploaded file is not a readable DOCX document.", error); }
    }
}
