package com.fileflow.app.processing;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import com.tom_roush.pdfbox.contentstream.PDFGraphicsStreamEngine;
import com.tom_roush.pdfbox.io.MemoryUsageSetting;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.pdmodel.PDPage;
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImage;
import com.tom_roush.pdfbox.util.Matrix;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import static com.fileflow.app.processing.LocalDocumentModel.*;
import static com.fileflow.app.processing.OfficeOpenXml.escapeXml;
import static com.fileflow.app.processing.OfficeOpenXml.put;

/** All processing stays on Android. No WebView, external executable, website or network. */
public final class LocalDocumentProcessor {
    public interface Progress { void update(String message); }
    private static final int MAX_INPUT = 20 * 1024 * 1024;
    private LocalDocumentProcessor() {}
    static byte[] input(ContentResolver resolver, Uri uri) throws IOException {
        InputStream stream = resolver.openInputStream(uri);
        if (stream == null) throw new IOException("The selected document is unavailable.");
        return BoundedStreams.read(stream, MAX_INPUT);
    }
    static void requireDocument(PDDocument pdf) throws IOException {
        if (pdf.isEncrypted()) throw new IOException("Password-protected PDFs are not supported.");
        if (pdf.getNumberOfPages() < 1 || pdf.getNumberOfPages() > 100) throw new IOException("Local conversion supports 1–100 pages. Split larger PDFs first.");
        if (!pdf.getCurrentAccessPermission().canExtractContent()) throw new IOException("This PDF does not allow text extraction.");
    }
    public static String extractTables(ContentResolver resolver, Uri uri, Progress progress) throws IOException {
        try (PDDocument pdf = PDDocument.load(new ByteArrayInputStream(input(resolver, uri)), MemoryUsageSetting.setupTempFileOnly())) { return extractTables(pdf, progress); }
    }
    public static String extractTables(ContentResolver resolver, Uri uri, boolean ocr, Progress progress) throws IOException {
        try (PDDocument pdf = PDDocument.load(new ByteArrayInputStream(input(resolver, uri)), MemoryUsageSetting.setupTempFileOnly())) { return extractTables(pdf, ocr, progress); }
    }
    public static String extractTables(PDDocument pdf, Progress progress) throws IOException {
        return extractTables(pdf, false, progress);
    }
    private static String extractTables(PDDocument pdf, boolean ocr, Progress progress) throws IOException {
        requireDocument(pdf); StringBuilder tsv = new StringBuilder(); int tables = 0; int ocrPages = 0;
        requireNoFormFields(pdf);
        for (int page = 1; page <= pdf.getNumberOfPages(); page++) {
            progress.update("Reading page " + page + " of " + pdf.getNumberOfPages());
            List<Line> lines = read(pdf, page);
            if (lines.isEmpty() && ocr) { if (++ocrPages > 10) throw new IOException("Process at most 10 scanned pages per OCR batch."); progress.update("Offline Latin OCR · page " + page + ". Review every recovered value."); lines = LocalOcr.read(pdf,page); }
            if (lines.isEmpty()) throw new IOException("Page " + page + " has no selectable text. OCR is required; no partial workbook was created.");
            for (int start = 0; start < lines.size();) {
                int end = tableEnd(lines, start);
                if (end == start) { start++; continue; }
                if (++tables > 100) throw new IOException("Export at most 100 tables.");
                if (tsv.length() > 0) tsv.append('\n');
                tsv.append(TableTsv.TABLE_MARKER).append(tables).append(" ===\n");
                for (List<List<Span>> row : tableRows(lines, start, end)) {
                    for (int c = 0; c < row.size(); c++) { if (c > 0) tsv.append('\t'); tsv.append(TableTsv.cell(text(row.get(c)))); }
                    tsv.append('\n');
                }
                if (tsv.length() > 2_000_000) throw new IOException("The extracted table exceeds the local review limit.");
                start = end;
            }
        }
        if (tables == 0) throw new IOException("No aligned multi-row tables were detected. Scans require OCR.");
        return tsv.toString();
    }
    public static byte[] pdfToDocx(ContentResolver resolver, Uri uri, Progress progress) throws IOException {
        try (PDDocument pdf = PDDocument.load(new ByteArrayInputStream(input(resolver, uri)), MemoryUsageSetting.setupTempFileOnly())) { return pdfToDocx(pdf, progress); }
    }
    public static byte[] pdfToDocx(ContentResolver resolver, Uri uri, boolean ocr, Progress progress) throws IOException {
        try (PDDocument pdf = PDDocument.load(new ByteArrayInputStream(input(resolver, uri)), MemoryUsageSetting.setupTempFileOnly())) { return pdfToDocx(pdf,ocr,progress); }
    }
    public static byte[] pdfToDocx(PDDocument pdf, Progress progress) throws IOException {
        return pdfToDocx(pdf,false,progress);
    }
    private static byte[] pdfToDocx(PDDocument pdf, boolean ocr, Progress progress) throws IOException {
        requireDocument(pdf); StringBuilder body = new StringBuilder(); List<byte[]> images = new ArrayList<>();
        requireNoFormFields(pdf);
        String relationships = ""; int chars = 0; int imageBytes = 0; int ocrPages = 0;
        for (int number = 1; number <= pdf.getNumberOfPages(); number++) {
            progress.update("Reconstructing page " + number + " of " + pdf.getNumberOfPages());
            PDPage page = pdf.getPage(number - 1); List<Line> lines = read(pdf, number);
            boolean recognized = lines.isEmpty() && ocr;
            if (recognized) { if (++ocrPages > 10) throw new IOException("Process at most 10 scanned pages per OCR batch."); progress.update("Offline Latin OCR · page " + number + ". Review every recovered word."); lines = LocalOcr.read(pdf,number); }
            if (lines.isEmpty()) throw new IOException("Page " + number + " has no selectable text. OCR is required; an image-only Word file was not created.");
            for (Line line : lines) chars += text(line.spans).length();
            if (chars > 2_000_000) throw new IOException("This document exceeds the local text limit.");
            // A scan becomes editable OCR text; do not place the entire scan behind or beside it.
            ImageCollector collector = new ImageCollector(page); if (!recognized) collector.processPage(page);
            int imageIndex = 0;
            for (int start = 0; start < lines.size();) {
                Line line = lines.get(start);
                while (imageIndex < collector.images.size() && collector.images.get(imageIndex).y <= line.y) {
                    ImageAsset image = collector.images.get(imageIndex++); images.add(image.png); body.append(imageParagraph(image, images.size(), page.getCropBox().getWidth()));
                }
                int end = tableEnd(lines, start);
                if (end > start) {
                    body.append("<w:tbl><w:tblPr><w:tblW w:w=\"0\" w:type=\"auto\"/></w:tblPr>");
                    List<List<List<Span>>> table = tableRows(lines, start, end);
                    body.append("<w:tblGrid>");
                    int columnWidth = Math.max(1, Math.round((page.getCropBox().getWidth() - 80) * 20 / table.get(0).size()));
                    for (int c = 0; c < table.get(0).size(); c++) body.append("<w:gridCol w:w=\"").append(columnWidth).append("\"/>");
                    body.append("</w:tblGrid>");
                    for (List<List<Span>> row : table) {
                        body.append("<w:tr>"); for (List<Span> cell : row) body.append("<w:tc><w:p>").append(runs(cell)).append("</w:p></w:tc>"); body.append("</w:tr>");
                    }
                    body.append("</w:tbl>"); start = end;
                } else {
                    body.append("<w:p><w:pPr><w:spacing w:after=\"0\" w:line=\"").append(Math.max(20, Math.round(line.size * 24))).append("\" w:lineRule=\"atLeast\"/></w:pPr>").append(runs(line.spans)).append("</w:p>"); start++;
                }
            }
            while (imageIndex < collector.images.size()) { ImageAsset image = collector.images.get(imageIndex++); images.add(image.png); body.append(imageParagraph(image, images.size(), page.getCropBox().getWidth())); }
            for (ImageAsset image : collector.images) imageBytes += image.png.length;
            if (imageBytes > 30 * 1024 * 1024) throw new IOException("Extracted images exceed the local document limit.");
            String section = "<w:sectPr><w:type w:val=\"nextPage\"/><w:pgSz w:w=\"" + Math.round(page.getCropBox().getWidth() * 20) + "\" w:h=\"" + Math.round(page.getCropBox().getHeight() * 20) + "\"/><w:pgMar w:top=\"" + Math.max(0, Math.round((lines.get(0).y - lines.get(0).size) * 20)) + "\" w:right=\"400\" w:bottom=\"400\" w:left=\"" + Math.max(0, Math.round(lines.get(0).spans.get(0).x * 20)) + "\"/></w:sectPr>";
            body.append(number == pdf.getNumberOfPages() ? section : "<w:p><w:pPr>" + section + "</w:pPr></w:p>");
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            put(zip, "[Content_Types].xml", "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Default Extension=\"png\" ContentType=\"image/png\"/><Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/></Types>");
            put(zip, "_rels/.rels", "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"doc\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/></Relationships>");
            put(zip, "word/document.xml", "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\" xmlns:wp=\"http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing\" xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" xmlns:pic=\"http://schemas.openxmlformats.org/drawingml/2006/picture\"><w:body>" + body + "</w:body></w:document>");
            for (int i = 0; i < images.size(); i++) {
                zip.putNextEntry(new ZipEntry("word/media/image" + (i + 1) + ".png")); zip.write(images.get(i)); zip.closeEntry();
                relationships += "<Relationship Id=\"image" + (i + 1) + "\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" Target=\"media/image" + (i + 1) + ".png\"/>";
            }
            put(zip, "word/_rels/document.xml.rels", "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" + relationships + "</Relationships>");
        }
        return output.toByteArray();
    }
    private static String runs(List<Span> spans) throws IOException {
        StringBuilder xml = new StringBuilder(); Span previous = null;
        // Coalesce adjacent glyphs sharing a font into Word runs, keeping documents small.
        for (int i = 0; i < spans.size();) {
            Span first = spans.get(i); StringBuilder value = new StringBuilder(); int end = i;
            while (end < spans.size() && spans.get(end).font.equals(first.font) && Math.abs(spans.get(end).size - first.size) < .1f) {
                Span span = spans.get(end++);
                if (previous != null && span.x - previous.x - previous.width > span.size * .15f && !previous.text.endsWith(" ") && !span.text.startsWith(" ")) value.append(' ');
                value.append(span.text); previous = span;
            }
            String font = first.font.replaceFirst("^[A-Z]{6}\\+", "");
            xml.append("<w:r><w:rPr><w:rFonts w:ascii=\"").append(escapeXml(font)).append("\" w:hAnsi=\"").append(escapeXml(font)).append("\"/><w:sz w:val=\"").append(Math.max(2, Math.round(first.size * 2))).append("\"/>");
            if (font.toLowerCase(java.util.Locale.ROOT).contains("bold")) xml.append("<w:b/>");
            if (font.toLowerCase(java.util.Locale.ROOT).matches(".*(italic|oblique).*")) xml.append("<w:i/>");
            xml.append("</w:rPr><w:t xml:space=\"preserve\">").append(escapeXml(value.toString())).append("</w:t></w:r>"); i = end;
        }
        return xml.toString();
    }
    private static void requireNoFormFields(PDDocument pdf) throws IOException {
        com.tom_roush.pdfbox.pdmodel.interactive.form.PDAcroForm form = pdf.getDocumentCatalog().getAcroForm();
        if (form != null && !form.getFields().isEmpty()) throw new IOException("PDF form fields are not supported by local editable conversion. Export a static copy from a PDF reader first.");
    }
    private static String imageParagraph(ImageAsset image, int id, float pageWidth) {
        float scale = Math.min(1, Math.min((pageWidth - 60) / Math.max(1, image.width), 650 / Math.max(1, image.height)));
        long width = Math.max(1, Math.round(image.width * scale * 12700)); long height = Math.max(1, Math.round(image.height * scale * 12700));
        return "<w:p><w:r><w:drawing><wp:inline><wp:extent cx=\"" + width + "\" cy=\"" + height + "\"/><wp:docPr id=\"" + id + "\" name=\"Image " + id + "\"/><a:graphic><a:graphicData uri=\"http://schemas.openxmlformats.org/drawingml/2006/picture\"><pic:pic><pic:nvPicPr><pic:cNvPr id=\"0\" name=\"Image\"/><pic:cNvPicPr/></pic:nvPicPr><pic:blipFill><a:blip r:embed=\"image" + id + "\"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill><pic:spPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"" + width + "\" cy=\"" + height + "\"/></a:xfrm><a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></pic:spPr></pic:pic></a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p>";
    }
    private static final class ImageAsset { byte[] png; float width, height, y; }
    private static final class ImageCollector extends PDFGraphicsStreamEngine {
        final List<ImageAsset> images = new ArrayList<>(); final Path path = new Path(); long pixels;
        ImageCollector(PDPage page) { super(page); }
        @Override public void drawImage(PDImage image) throws IOException {
            if ((pixels += (long) image.getWidth() * image.getHeight()) > 16_000_000 || images.size() >= 50) throw new IOException("Embedded images exceed the safe local limit.");
            Bitmap bitmap = image.getImage();
            try (ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, bytes)) throw new IOException("An embedded image could not be exported.");
                Matrix matrix = getGraphicsState().getCurrentTransformationMatrix(); ImageAsset asset = new ImageAsset();
                asset.png = bytes.toByteArray(); asset.width = matrix.getScalingFactorX(); asset.height = matrix.getScalingFactorY(); asset.y = getPage().getCropBox().getHeight() - matrix.getTranslateY() - asset.height; images.add(asset);
            } // PDFBox owns/caches the source bitmap; do not recycle it here.
            images.sort(java.util.Comparator.comparingDouble(imageAsset -> imageAsset.y));
        }
        @Override public void appendRectangle(PointF a, PointF b, PointF c, PointF d) { path.moveTo(a.x,a.y); path.lineTo(b.x,b.y); path.lineTo(c.x,c.y); path.lineTo(d.x,d.y); path.close(); }
        @Override public void clip(Path.FillType rule) { }
        @Override public void moveTo(float x,float y) { path.moveTo(x,y); }
        @Override public void lineTo(float x,float y) { path.lineTo(x,y); }
        @Override public void curveTo(float a,float b,float c,float d,float e,float f) { path.cubicTo(a,b,c,d,e,f); }
        @Override public PointF getCurrentPoint() { return new PointF(0,0); }
        @Override public void closePath() { path.close(); }
        @Override public void endPath() { path.reset(); }
        @Override public void strokePath() { path.reset(); }
        @Override public void fillPath(Path.FillType rule) { path.reset(); }
        @Override public void fillAndStrokePath(Path.FillType rule) { path.reset(); }
        @Override public void shadingFill(com.tom_roush.pdfbox.cos.COSName name) { }
    }
    public static byte[] docxToPdf(ContentResolver resolver, Uri uri) throws Exception {
        byte[] source = input(resolver, uri);
        // Explicit text-only mode: reject objects that would otherwise be silently lost.
        try (java.util.zip.ZipInputStream zip = new java.util.zip.ZipInputStream(new ByteArrayInputStream(source))) {
            ZipEntry entry; int entries = 0; long expanded = 0;
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > 5000) throw new IOException("Too many Word archive entries.");
                byte[] bytes = BoundedStreams.read(new NonClosingInputStream(zip), 8 * 1024 * 1024); expanded += bytes.length;
                if (expanded > 40 * 1024 * 1024) throw new IOException("Word archive exceeds the expanded size limit.");
                String name = entry.getName();
                if (name.matches("word/media/.*")) throw new IOException("Local Word PDF export supports simple text only. Use Word or LibreOffice for images, tables, notes and headers.");
                if (name.equals("word/document.xml")) {
                    String xml = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
                    if (xml.matches("(?s).*<(?:[A-Za-z0-9_]+:)?(?:tbl|drawing|pict|object|altChunk|fldChar|sym|footnoteReference|endnoteReference|headerReference|footerReference)(?:[\\s/>]).*")) throw new IOException("This document needs a full Word renderer. Local export supports simple text only.");
                }
            }
        }
        String text = OfficeOpenXml.extractDocxText(new ByteArrayInputStream(source));
        if (text.length() > 1_000_000) throw new IOException("Word text exceeds the local export limit.");
        TextPaint paint = new TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG); paint.setTextSize(11); paint.setColor(android.graphics.Color.BLACK);
        StaticLayout layout = StaticLayout.Builder.obtain(text, 0, text.length(), paint, 487).setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false).build();
        PdfDocument pdf = new PdfDocument();
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            int line = 0; int pageNumber = 0;
            while (line < layout.getLineCount()) {
                if (++pageNumber > 100) throw new IOException("Word export exceeds the 100-page local limit.");
                int top = layout.getLineTop(line); int end = line + 1;
                while (end < layout.getLineCount() && layout.getLineBottom(end) - top <= 734) end++;
                int bottom = layout.getLineBottom(end - 1);
                PdfDocument.Page page = pdf.startPage(new PdfDocument.PageInfo.Builder(595, 842, pageNumber).create());
                page.getCanvas().save(); page.getCanvas().translate(54,54); page.getCanvas().clipRect(0,0,487,bottom-top); page.getCanvas().translate(0,-top); layout.draw(page.getCanvas()); page.getCanvas().restore(); pdf.finishPage(page); line = end;
            }
            pdf.writeTo(bytes); return bytes.toByteArray();
        } finally { pdf.close(); }
    }
    private static final class NonClosingInputStream extends java.io.FilterInputStream {
        NonClosingInputStream(InputStream input) { super(input); }
        @Override public void close() { }
    }
}
