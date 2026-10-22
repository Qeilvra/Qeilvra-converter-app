package com.fileflow.server.service;

import com.fileflow.server.config.ProcessingProperties;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.springframework.stereotype.Service;

@Service
public class OfficeConversionService {
    private final ProcessingProperties properties;
    private final ProcessRunner runner;
    private final CapabilityService health;

    public OfficeConversionService(ProcessingProperties properties, ProcessRunner runner, CapabilityService health) {
        this.properties = properties;
        this.runner = runner;
        this.health = health;
    }

    public byte[] pdfToDocx(byte[] input) {
        try (PDDocument document = PDDocument.load(new ByteArrayInputStream(input), MemoryUsageSetting.setupTempFileOnly())) {
            if (!hasText(document))
                throw new ProcessingException("This PDF has no extractable text. Scanned PDFs need OCR, which is not available in this service.");
        } catch (IOException error) {
            throw new ProcessingException("The PDF could not be read for Word conversion.", error);
        }
        health.require("pdf-to-docx");
        try (JobWorkspace workspace = JobWorkspace.create()) {
            Path source = workspace.resolve("source.pdf");
            Path output = workspace.resolve("result.docx");
            Files.write(source, input);
            List<String> command = parseTemplate(properties.pdfToDocxCommand(), source, output);
            runner.run(command, workspace.root(), "PDF to Word conversion");
            return requireDocxOutput(output);
        } catch (IOException error) {
            throw new ProcessingException("PDF to Word conversion could not be completed.", error);
        }
    }

    private static boolean hasText(PDDocument document) throws IOException {
        // Stop at the first printable character; do not materialize all text just to detect scans.
        try {
            new PDFTextStripper().writeText(document, new Writer() {
                @Override public void write(char[] chars, int offset, int count) throws IOException {
                    for (int index = offset; index < offset + count; index++)
                        if (!Character.isWhitespace(chars[index])) throw new TextFound();
                }
                @Override public void flush() { }
                @Override public void close() { }
            });
            return false;
        } catch (TextFound found) { return true; }
    }

    private static final class TextFound extends IOException { }

    public byte[] docxToPdf(byte[] input) {
        health.require("docx-to-pdf");
        try (JobWorkspace workspace = JobWorkspace.create()) {
            Path source = workspace.resolve("source.docx");
            Path profile = workspace.resolve("lo-profile");
            Path output = workspace.resolve("source.pdf");
            Files.write(source, input);
            Files.createDirectories(profile);
            List<String> command = List.of(
                    properties.libreOfficeCommand(), "--headless", "--nologo", "--nodefault", "--nolockcheck", "--nofirststartwizard",
                    "-env:UserInstallation=" + profile.toUri(),
                    "--convert-to", "pdf:writer_pdf_Export",
                    "--outdir", workspace.root().toString(), source.toString());
            runner.run(command, workspace.root(), "Word to PDF conversion");
            return requirePdfOutput(output);
        } catch (IOException error) {
            throw new ProcessingException("Word to PDF conversion could not be completed.", error);
        }
    }

    static List<String> parseTemplate(String template, Path input, Path output) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < template.length(); index++) {
            char character = template.charAt(index);
            if (character == '"') quoted = !quoted;
            else if (Character.isWhitespace(character) && !quoted) {
                if (!current.isEmpty()) { parts.add(current.toString()); current.setLength(0); }
            } else current.append(character);
        }
        if (!current.isEmpty()) parts.add(current.toString());
        if (quoted || parts.isEmpty() || !template.contains("{input}") || !template.contains("{output}")) throw new ProcessingException("The PDF-to-DOCX engine command is invalid.");
        return parts.stream().map(value -> value.replace("{input}", input.toString()).replace("{output}", output.toString())).toList();
    }

    private static byte[] requireDocxOutput(Path output) throws IOException {
        byte[] bytes = requireOutput(output, "The PDF-to-Word engine did not produce a DOCX file.");
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            if (document.getBodyElements().isEmpty()) throw new ProcessingException("The PDF-to-Word engine produced an empty document.");
            try (XWPFWordExtractor text = new XWPFWordExtractor(document)) {
                if (text.getText().isBlank()) throw new ProcessingException("The PDF-to-Word result contains no editable text. OCR or a different source document is required.");
            }
        } catch (ProcessingException error) {
            throw error;
        } catch (Exception error) {
            throw new ProcessingException("The PDF-to-Word result is not a valid DOCX file.", error);
        }
        return bytes;
    }

    private static byte[] requirePdfOutput(Path output) throws IOException {
        byte[] bytes = requireOutput(output, "LibreOffice did not produce a PDF file.");
        try (PDDocument document = PDDocument.load(new ByteArrayInputStream(bytes), MemoryUsageSetting.setupTempFileOnly())) {
            if (document.getNumberOfPages() == 0) throw new ProcessingException("LibreOffice produced an empty PDF.");
        } catch (ProcessingException error) {
            throw error;
        } catch (IOException error) {
            throw new ProcessingException("The Word-to-PDF result is not a valid PDF file.", error);
        }
        return bytes;
    }

    private static byte[] requireOutput(Path output, String message) throws IOException {
        if (!Files.isRegularFile(output) || Files.size(output) == 0) throw new ProcessingException(message);
        if (Files.size(output) > 100L * 1024 * 1024) throw new ProcessingException("The converted output exceeds the 100 MB safe download limit. Split the source document and try again.");
        return Files.readAllBytes(output);
    }
}
