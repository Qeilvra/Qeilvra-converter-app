package com.fileflow.app.processing;

import android.content.ContentResolver;
import android.net.Uri;
import androidx.annotation.NonNull;
import com.tom_roush.pdfbox.io.MemoryUsageSetting;
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility;
import com.tom_roush.pdfbox.multipdf.Splitter;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.pdmodel.PDPage;
import com.tom_roush.pdfbox.text.PDFTextStripper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class PdfProcessor {
    public static final int MAX_PAGES = 500;

    private PdfProcessor() {}

    @NonNull
    public static String extractText(@NonNull ContentResolver resolver, @NonNull Uri uri) throws IOException {
        try (InputStream input = requireInput(resolver, uri);
             PDDocument document = PDDocument.load(input, MemoryUsageSetting.setupTempFileOnly())) {
            requirePageLimit(document);
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String text = stripper.getText(document).trim();
            if (text.isEmpty()) throw new IOException("No selectable text was found. This PDF may require OCR.");
            return text;
        }
    }

    @NonNull
    public static String extractTableTsv(@NonNull ContentResolver resolver, @NonNull Uri uri) throws IOException {
        String text = extractText(resolver, uri);
        StringBuilder table = new StringBuilder();
        int tableRows = 0;
        for (String rawLine : text.replace("\r\n", "\n").replace('\r', '\n').split("\n")) {
            String line = rawLine.trim();
            if (line.isEmpty()) continue;
            String[] cells = line.split("(?:\\t+|\\s{2,})");
            if (cells.length < 2) continue;
            if (table.length() > 0) table.append('\n');
            for (int index = 0; index < cells.length; index++) {
                if (index > 0) table.append('\t');
                table.append(cells[index].trim());
            }
            tableRows++;
        }
        if (tableRows == 0) throw new IOException("No table-like rows were detected. Review a text-based PDF with aligned columns.");
        return table.toString();
    }

    @NonNull
    public static byte[] optimize(@NonNull ContentResolver resolver, @NonNull Uri uri) throws IOException {
        try (InputStream input = requireInput(resolver, uri);
             PDDocument document = PDDocument.load(input, MemoryUsageSetting.setupTempFileOnly());
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            requirePageLimit(document);
            document.setAllSecurityToBeRemoved(false);
            document.save(output);
            return output.toByteArray();
        }
    }

    @NonNull
    public static byte[] merge(@NonNull ContentResolver resolver, @NonNull List<Uri> uris) throws IOException {
        if (uris.size() < 2) throw new IOException("Select at least two PDFs to merge.");
        if (uris.size() > 20) throw new IOException("Merge at most 20 PDFs at once.");
        PDFMergerUtility merger = new PDFMergerUtility();
        List<InputStream> inputs = new ArrayList<>();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            for (Uri uri : uris) {
                InputStream input = requireInput(resolver, uri);
                inputs.add(input);
                merger.addSource(input);
            }
            merger.setDestinationStream(output);
            merger.mergeDocuments(MemoryUsageSetting.setupTempFileOnly());
            return output.toByteArray();
        } finally {
            for (InputStream input : inputs) try { input.close(); } catch (IOException ignored) {}
        }
    }

    @NonNull
    public static byte[] rotateAll(@NonNull ContentResolver resolver, @NonNull Uri uri, int clockwiseDegrees) throws IOException {
        try (InputStream input = requireInput(resolver, uri);
             PDDocument document = PDDocument.load(input, MemoryUsageSetting.setupTempFileOnly());
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            requirePageLimit(document);
            for (PDPage page : document.getPages()) page.setRotation((page.getRotation() + clockwiseDegrees) % 360);
            document.save(output);
            return output.toByteArray();
        }
    }

    public static int pageCount(@NonNull ContentResolver resolver, @NonNull Uri uri) throws IOException {
        try (InputStream input = requireInput(resolver, uri); PDDocument document = PDDocument.load(input, MemoryUsageSetting.setupTempFileOnly())) {
            requirePageLimit(document);
            return document.getNumberOfPages();
        }
    }

    @NonNull
    public static byte[] editPages(@NonNull ContentResolver resolver, @NonNull Uri uri, @NonNull List<Integer> pageIndexes, @NonNull List<Integer> rotations) throws IOException {
        if (pageIndexes.isEmpty()) throw new IOException("Keep at least one page.");
        if (pageIndexes.size() != rotations.size()) throw new IOException("The page edit list is inconsistent.");
        try (InputStream input = requireInput(resolver, uri);
             PDDocument source = PDDocument.load(input, MemoryUsageSetting.setupTempFileOnly());
             PDDocument output = new PDDocument(MemoryUsageSetting.setupTempFileOnly());
             ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            requirePageLimit(source);
            for (int index = 0; index < pageIndexes.size(); index++) {
                int pageIndex = pageIndexes.get(index);
                if (pageIndex < 0 || pageIndex >= source.getNumberOfPages()) throw new IOException("A selected page is out of range.");
                PDPage page = output.importPage(source.getPage(pageIndex));
                page.setRotation((page.getRotation() + rotations.get(index)) % 360);
            }
            output.save(bytes);
            return bytes.toByteArray();
        }
    }

    @NonNull
    public static byte[] splitToZip(@NonNull ContentResolver resolver, @NonNull Uri uri) throws IOException {
        try (InputStream input = requireInput(resolver, uri);
             PDDocument document = PDDocument.load(input, MemoryUsageSetting.setupTempFileOnly());
             ByteArrayOutputStream output = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(output)) {
            requirePageLimit(document);
            List<PDDocument> pages = new Splitter().split(document);
            int digits = String.valueOf(pages.size()).length();
            try {
                for (int index = 0; index < pages.size(); index++) {
                    zip.putNextEntry(new ZipEntry(String.format(Locale.US, "page-%0" + digits + "d.pdf", index + 1)));
                    pages.get(index).save(zip);
                    zip.closeEntry();
                }
            } finally {
                for (PDDocument page : pages) try { page.close(); } catch (IOException ignored) {}
            }
            zip.finish();
            return output.toByteArray();
        }
    }

    private static void requirePageLimit(PDDocument document) throws IOException {
        if (document.isEncrypted()) throw new IOException("Password-protected PDFs are not supported.");
        if (document.getNumberOfPages() > MAX_PAGES) throw new IOException("This PDF exceeds the 500-page local processing limit.");
    }

    private static InputStream requireInput(ContentResolver resolver, Uri uri) throws IOException {
        InputStream input = resolver.openInputStream(uri);
        if (input == null) throw new IOException("The selected file is unavailable.");
        return input;
    }
}
