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
import static com.fileflow.app.processing.LocalDocumentModel.*;

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
        requireDocument(pdf); requireNoFormFields(pdf);
        List<LocalWordDocument.Page> pages = new ArrayList<>();
        int chars = 0, imageBytes = 0, ocrPages = 0;
        for (int number = 1; number <= pdf.getNumberOfPages(); number++) {
            progress.update("Reconstructing page " + number + " of " + pdf.getNumberOfPages());
            PDPage page = pdf.getPage(number - 1); List<Line> lines = read(pdf, number, true);
            boolean recognized = lines.isEmpty() && ocr;
            if (recognized) {
                if (++ocrPages > 10) throw new IOException("Process at most 10 scanned pages per OCR batch.");
                progress.update("Offline Latin OCR - page " + number + ". Review every recovered word.");
                lines = LocalOcr.read(pdf,number);
            }
            if (lines.isEmpty()) throw new IOException("Page " + number + " has no selectable text. OCR is required; an image-only Word file was not created.");
            for (Line line : lines) chars += text(line.spans).length();
            if (chars > 2_000_000) throw new IOException("This document exceeds the local text limit.");
            ImageCollector collector = new ImageCollector(page);
            if (!recognized) collector.processPage(page);
            for (LocalWordDocument.Image image : collector.images) imageBytes += image.png.length;
            if (imageBytes > 30 * 1024 * 1024) throw new IOException("Extracted images exceed the local document limit.");
            LocalWordDocument.Page model = new LocalWordDocument.Page();
            model.width = page.getCropBox().getWidth(); model.height = page.getCropBox().getHeight();
            model.lines = lines; model.images = collector.images; model.rules = collector.rules;
            for (Line line : lines) for (Span span : line.spans) for (WordLayout.Rule rule : model.rules)
                if (rule.height < .3f && rule.y-span.y >= -.5f && rule.y-span.y <= span.size*.25f && rule.x <= span.x+2 && rule.x+rule.width >= span.x+span.width-2) span.underline = true;
            model.blocks = WordLayout.reconstruct(lines,model.rules);
            for (WordLayout.Rule rule : model.rules) {
                boolean represented = false;
                for (Line line : lines) for (Span span : line.spans) if (span.underline && Math.abs(rule.y-span.y) < span.size*.25f && rule.x <= span.x+2 && rule.x+rule.width >= span.x+span.width-2) represented = true;
                for (WordLayout.Block block : model.blocks) if (block.rows != null && rule.y >= block.y-2 && rule.y <= block.lines.get(block.lines.size()-1).y+block.lines.get(0).size && rule.x >= block.x-2 && rule.x <= block.x+block.width+2) represented = true;
                if (represented) continue;
                // A tiny independent asset preserves a rule; text and page content remain editable.
                Bitmap pixel = Bitmap.createBitmap(1,1,Bitmap.Config.ARGB_8888);
                try (ByteArrayOutputStream png = new ByteArrayOutputStream()) {
                    pixel.eraseColor((int)(0xff000000L | Long.parseLong(rule.color,16)));
                    if (!pixel.compress(Bitmap.CompressFormat.PNG,100,png)) throw new IOException("A PDF rule could not be exported.");
                    LocalWordDocument.Image image = new LocalWordDocument.Image(); image.png = png.toByteArray(); image.x = rule.x; image.y = rule.y;
                    image.width = Math.max(rule.width,rule.thickness); image.height = Math.max(rule.height,rule.thickness); model.images.add(image);
                    imageBytes += image.png.length;
                } finally { pixel.recycle(); }
                if (imageBytes > 30 * 1024 * 1024) throw new IOException("Extracted graphics exceed the local document limit.");
            }
            pages.add(model);
        }
        progress.update("Creating editable Word file");
        return LocalWordDocument.create(pages);
    }
    private static void requireNoFormFields(PDDocument pdf) throws IOException {
        com.tom_roush.pdfbox.pdmodel.interactive.form.PDAcroForm form = pdf.getDocumentCatalog().getAcroForm();
        if (form != null && !form.getFields().isEmpty()) throw new IOException("PDF form fields are not supported by local editable conversion. Export a static copy from a PDF reader first.");
    }
    private static final class ImageCollector extends PDFGraphicsStreamEngine {
        final List<LocalWordDocument.Image> images = new ArrayList<>(); final List<WordLayout.Rule> rules = new ArrayList<>(); final Path path = new Path(); long pixels;
        float startX, startY, endX, endY; boolean straight;
        ImageCollector(PDPage page) { super(page); }
        @Override public void drawImage(PDImage image) throws IOException {
            if ((pixels += (long) image.getWidth() * image.getHeight()) > 16_000_000 || images.size() >= 50) throw new IOException("Embedded images exceed the safe local limit.");
            Bitmap bitmap = image.getImage();
            try (ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, bytes)) throw new IOException("An embedded image could not be exported.");
                Matrix matrix = getGraphicsState().getCurrentTransformationMatrix(); LocalWordDocument.Image asset = new LocalWordDocument.Image();
                if (Math.abs(matrix.getShearX()) > .2f || Math.abs(matrix.getShearY()) > .2f || matrix.getScaleX() < 0 || matrix.getScaleY() < 0) throw new IOException("Rotated or mirrored embedded images are not supported by local editable conversion.");
                asset.x = matrix.getTranslateX() - getPage().getCropBox().getLowerLeftX();
                asset.png = bytes.toByteArray(); asset.width = matrix.getScalingFactorX(); asset.height = matrix.getScalingFactorY(); asset.y = getPage().getCropBox().getUpperRightY() - matrix.getTranslateY() - asset.height; images.add(asset);
            } // PDFBox owns/caches the source bitmap; do not recycle it here.
            images.sort(java.util.Comparator.comparingDouble(imageAsset -> imageAsset.y));
        }
        @Override public void appendRectangle(PointF a, PointF b, PointF c, PointF d) { path.moveTo(a.x,a.y); path.lineTo(b.x,b.y); path.lineTo(c.x,c.y); path.lineTo(d.x,d.y); path.close(); }
        @Override public void clip(Path.FillType rule) { }
        @Override public void moveTo(float x,float y) { path.moveTo(x,y); startX = endX = x; startY = endY = y; straight = true; }
        @Override public void lineTo(float x,float y) { path.lineTo(x,y); if (endX != startX || endY != startY) straight = false; endX = x; endY = y; }
        @Override public void curveTo(float a,float b,float c,float d,float e,float f) { path.cubicTo(a,b,c,d,e,f); straight = false; }
        @Override public PointF getCurrentPoint() { return new PointF(endX,endY); }
        @Override public void closePath() { straight = false; path.close(); }
        @Override public void endPath() { straight = false; path.reset(); }
        @Override public void strokePath() throws IOException {
            if (straight && (Math.abs(endX-startX)<.3f || Math.abs(endY-startY)<.3f)) {
                WordLayout.Rule rule = new WordLayout.Rule(); rule.x = Math.min(startX,endX)-getPage().getCropBox().getLowerLeftX(); rule.y = getPage().getCropBox().getUpperRightY()-Math.max(startY,endY);
                rule.width = Math.abs(endX-startX); rule.height = Math.abs(endY-startY); rule.thickness = getGraphicsState().getLineWidth();
                rule.color = String.format(java.util.Locale.ROOT,"%06X",getGraphicsState().getStrokingColor().toRGB()&0xffffff);
                if (rule.width > 3 || rule.height > 3) { if (rules.size() >= 2000) throw new IOException("PDF rules exceed the safe local conversion limit."); rules.add(rule); }
            }
            straight = false; path.reset();
        }
        @Override public void fillPath(Path.FillType rule) { straight = false; path.reset(); }
        @Override public void fillAndStrokePath(Path.FillType rule) throws IOException { strokePath(); }
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
