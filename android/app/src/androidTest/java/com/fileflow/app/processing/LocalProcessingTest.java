package com.fileflow.app.processing;

import static org.junit.Assert.*;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import androidx.exifinterface.media.ExifInterface;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Requires an Android device: these tests exercise native codecs and PDFBox, not mocks. */
@RunWith(AndroidJUnit4.class)
public final class LocalProcessingTest {
    private final Context context = ApplicationProvider.getApplicationContext();

    @Test public void bundledOcrRecoversEditableScanTextWithoutFlatteningWord() throws Exception {
        org.junit.Assume.assumeTrue(com.fileflow.app.BuildConfig.BUNDLED_OFFLINE_OCR);
        File source = File.createTempFile("ocr-test-", ".pdf", context.getCacheDir());
        Bitmap scan = Bitmap.createBitmap(1200,1600,Bitmap.Config.RGB_565); scan.eraseColor(Color.WHITE);
        android.graphics.Canvas canvas = new android.graphics.Canvas(scan); android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG); paint.setColor(Color.BLACK); paint.setTextSize(40);
        canvas.drawText("ID",80,180,paint); canvas.drawText("Value",720,180,paint);
        canvas.drawText("001234",80,270,paint); canvas.drawText("92",720,270,paint);
        canvas.drawText("009876",80,360,paint); canvas.drawText("87",720,360,paint);
        try {
            try (PDDocument pdf = new PDDocument()) {
                com.tom_roush.pdfbox.pdmodel.PDPage page = new com.tom_roush.pdfbox.pdmodel.PDPage(); pdf.addPage(page);
                try (com.tom_roush.pdfbox.pdmodel.PDPageContentStream content = new com.tom_roush.pdfbox.pdmodel.PDPageContentStream(pdf,page)) { content.drawImage(com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory.createFromImage(pdf,scan),0,0,612,792); }
                pdf.save(source);
            }
            String tsv = LocalDocumentProcessor.extractTables(context.getContentResolver(),Uri.fromFile(source),true,message -> { });
            assertTrue(tsv.contains("001234"));
            byte[] word = LocalDocumentProcessor.pdfToDocx(context.getContentResolver(),Uri.fromFile(source),true,message -> { });
            String xml = zipText(word,"word/document.xml"); assertTrue(xml.contains("001234")); assertFalse(xml.contains("<w:drawing>"));
        } finally { scan.recycle(); assertTrue(source.delete()); }
    }

    @Test public void offlineOfficeRecoveryKeepsEditableTextTablesImagesAndExactIdentifiers() throws Exception {
        try (PDDocument source = new PDDocument()) {
            Bitmap bitmap = Bitmap.createBitmap(40, 30, Bitmap.Config.RGB_565); bitmap.eraseColor(Color.BLUE);
            try {
                com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject image = com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory.createFromImage(source, bitmap);
                for (int p = 0; p < 2; p++) {
                    com.tom_roush.pdfbox.pdmodel.PDPage page = new com.tom_roush.pdfbox.pdmodel.PDPage(); source.addPage(page);
                    try (com.tom_roush.pdfbox.pdmodel.PDPageContentStream content = new com.tom_roush.pdfbox.pdmodel.PDPageContentStream(source, page)) {
                        String[][] rows = {{"ID", "Value"}, {"001234", "$1,234.50"}, {"12345678901234567890", "=SUM(A1:A2)"}};
                        for (int r = 0; r < rows.length; r++) for (int c = 0; c < 2; c++) {
                            content.beginText(); content.setFont(com.tom_roush.pdfbox.pdmodel.font.PDType1Font.HELVETICA, 11); content.newLineAtOffset(c == 0 ? 50 : 330, 700 - r * 26); content.showText(rows[r][c]); content.endText();
                        }
                        content.drawImage(image, 50, 450, 40, 30);
                    }
                }
                String tsv = LocalDocumentProcessor.extractTables(source, message -> { });
                assertTrue(tsv.contains("001234")); assertTrue(tsv.contains("12345678901234567890"));
                assertEquals(2, TableTsv.parseSheets(tsv).size());
                byte[] workbook = OfficeOpenXml.createXlsxFromTsv(tsv);
                byte[] word = LocalDocumentProcessor.pdfToDocx(source, message -> { });
                OfficeOpenXml.validateDocx(word);
                String xml = zipText(word, "word/document.xml");
                assertTrue(xml.contains("001234")); assertTrue(xml.contains("12345678901234567890"));
                assertTrue(xml.contains("<w:tbl>")); assertTrue(xml.contains("<w:t xml:space=\"preserve\">")); assertTrue(xml.contains("<w:drawing>"));
                assertNotNull(zipText(word, "word/media/image1.png"));
                assertTrue(zipText(workbook, "xl/worksheets/sheet1.xml").contains("001234"));
                assertTrue(zipText(workbook, "xl/worksheets/sheet2.xml").contains("12345678901234567890"));
            } finally { bitmap.recycle(); }
        }
        try (PDDocument scan = new PDDocument()) {
            scan.addPage(new com.tom_roush.pdfbox.pdmodel.PDPage());
            assertThrows(java.io.IOException.class, () -> LocalDocumentProcessor.pdfToDocx(scan, message -> { }));
            assertThrows(java.io.IOException.class, () -> LocalDocumentProcessor.extractTables(scan, message -> { }));
        }
    }

    @Test public void offlinePdfCompressionKeepsSelectableTextAndReportsActualReduction() throws Exception {
        byte[] original;
        try (PDDocument source = new PDDocument(); java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream()) {
            com.tom_roush.pdfbox.pdmodel.PDPage page = new com.tom_roush.pdfbox.pdmodel.PDPage(); source.addPage(page);
            Bitmap bitmap = Bitmap.createBitmap(900, 700, Bitmap.Config.RGB_565);
            java.util.Random random = new java.util.Random(7);
            for (int y = 0; y < 700; y++) for (int x = 0; x < 900; x++) bitmap.setPixel(x,y,Color.rgb(random.nextInt(256),random.nextInt(256),random.nextInt(256)));
            try (com.tom_roush.pdfbox.pdmodel.PDPageContentStream content = new com.tom_roush.pdfbox.pdmodel.PDPageContentStream(source,page)) {
                content.drawImage(com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory.createFromImage(source,bitmap),50,100,450,350);
                content.beginText(); content.setFont(com.tom_roush.pdfbox.pdmodel.font.PDType1Font.HELVETICA,12); content.newLineAtOffset(50,700); content.showText("Editable text survives compression 001234"); content.endText();
            } finally { bitmap.recycle(); }
            source.save(bytes); original = bytes.toByteArray();
        }
        LocalPdfCompressor.Result compressed = LocalPdfCompressor.compress(original,1);
        assertEquals(compressed.bytes.length < original.length, compressed.reduced);
        try (PDDocument pdf = PDDocument.load(compressed.bytes)) { assertTrue(new com.tom_roush.pdfbox.text.PDFTextStripper().getText(pdf).contains("001234")); }
    }

    private static String zipText(byte[] bytes, String name) throws Exception {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) if (name.equals(entry.getName())) return new String(BoundedStreams.read(new NonClosingInputStream(zip), 20_000_000), java.nio.charset.StandardCharsets.UTF_8);
        }
        throw new AssertionError("Missing package part " + name);
    }

    @Test public void imageFormatsRoundTripAndPngPreservesTransparency() throws Exception {
        File source = image(120, 80, Bitmap.CompressFormat.PNG, Color.TRANSPARENT);
        try {
            for (String format : new String[]{"jpg", "png", "webp"}) {
                byte[] bytes = BitmapProcessor.convert(context.getContentResolver(), new BitmapProcessor.ImageEdit(Uri.fromFile(source)), format, 90, 4096);
                Bitmap result = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                assertNotNull(result);
                assertEquals(120, result.getWidth()); assertEquals(80, result.getHeight());
                if ("png".equals(format)) assertEquals(0, Color.alpha(result.getPixel(0, 0)));
                if ("jpg".equals(format)) assertTrue(Color.red(result.getPixel(0, 0)) >= 250);
                result.recycle();
            }
        } finally { assertTrue(source.delete()); }
    }

    @Test public void uploadDimensionsApplyAfterExifRotation() throws Exception {
        File source = image(320, 100, Bitmap.CompressFormat.JPEG, Color.BLUE);
        try {
            ExifInterface exif = new ExifInterface(source);
            exif.setAttribute(ExifInterface.TAG_ORIENTATION, String.valueOf(ExifInterface.ORIENTATION_ROTATE_90)); exif.saveAttributes();
            ImageCompressor.Result result = ImageCompressor.compress(context.getContentResolver(), Uri.fromFile(source), 20 * 1024, 90, 150, 80);
            assertTrue(result.width <= 150); assertTrue(result.height <= 80);
            assertTrue(result.height > result.width); assertTrue(result.reachedTarget);
            Bitmap decoded = BitmapFactory.decodeByteArray(result.bytes, 0, result.bytes.length);
            assertEquals(result.width, decoded.getWidth()); assertEquals(result.height, decoded.getHeight()); decoded.recycle();
        } finally { assertTrue(source.delete()); }
    }

    @Test public void uploadHonorsUserRotationAndRejectsCrossedScanCorners() throws Exception {
        File source = image(320, 100, Bitmap.CompressFormat.JPEG, Color.BLUE);
        try {
            BitmapProcessor.ImageEdit edit = new BitmapProcessor.ImageEdit(Uri.fromFile(source)); edit.rotation = 90;
            ImageCompressor.Result result = ImageCompressor.compress(context.getContentResolver(), edit, 20 * 1024, 90, 150, 80);
            assertTrue(result.width <= 150); assertTrue(result.height <= 80); assertTrue(result.height > result.width);
            edit.perspective = true; edit.topLeftX = 0; edit.topLeftY = 0; edit.topRightX = 100; edit.topRightY = 100;
            edit.bottomRightX = 100; edit.bottomRightY = 0; edit.bottomLeftX = 0; edit.bottomLeftY = 100;
            assertThrows(java.io.IOException.class, () -> BitmapProcessor.imagesToPdf(context.getContentResolver(), Collections.singletonList(edit)));
        } finally { assertTrue(source.delete()); }
    }

    @Test public void pdfCreateMergeReorderRotateDeleteAndSplitRemainReadable() throws Exception {
        File source = image(160, 100, Bitmap.CompressFormat.PNG, Color.WHITE);
        File pdf = File.createTempFile("local-test-", ".pdf", context.getCacheDir());
        try {
            BitmapProcessor.ImageEdit edit = new BitmapProcessor.ImageEdit(Uri.fromFile(source));
            byte[] bytes = BitmapProcessor.imagesToPdf(context.getContentResolver(), Arrays.asList(edit, edit, edit));
            try (FileOutputStream output = new FileOutputStream(pdf)) { output.write(bytes); }
            Uri uri = Uri.fromFile(pdf);
            assertEquals(3, PdfProcessor.pageCount(context.getContentResolver(), uri));
            byte[] edited = PdfProcessor.editPages(context.getContentResolver(), uri, Arrays.asList(2, 0), Arrays.asList(90, 0));
            try (PDDocument result = PDDocument.load(edited)) { assertEquals(2, result.getNumberOfPages()); assertEquals(90, result.getPage(0).getRotation()); }
            try (PDDocument result = PDDocument.load(PdfProcessor.merge(context.getContentResolver(), Arrays.asList(uri, uri)))) { assertEquals(6, result.getNumberOfPages()); }
            int count = 0;
            try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(PdfProcessor.splitToZip(context.getContentResolver(), uri)))) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    byte[] page = BoundedStreams.read(new NonClosingInputStream(zip), 2 * 1024 * 1024);
                    try (PDDocument result = PDDocument.load(page)) { assertEquals(1, result.getNumberOfPages()); }
                    count++;
                }
            }
            assertEquals(3, count);
            assertThrows(java.io.IOException.class, () -> PdfProcessor.editPages(context.getContentResolver(), uri, Collections.emptyList(), Collections.emptyList()));
        } finally { assertTrue(source.delete()); assertTrue(pdf.delete()); }
    }

    @Test public void pageEditingPreservesInheritedFontResources() throws Exception {
        File pdf = File.createTempFile("inherited-resources-", ".pdf", context.getCacheDir());
        try {
            try (PDDocument source = new PDDocument()) {
                com.tom_roush.pdfbox.pdmodel.PDPage page = new com.tom_roush.pdfbox.pdmodel.PDPage(); source.addPage(page);
                try (com.tom_roush.pdfbox.pdmodel.PDPageContentStream content = new com.tom_roush.pdfbox.pdmodel.PDPageContentStream(source, page)) {
                    content.beginText(); content.setFont(com.tom_roush.pdfbox.pdmodel.font.PDType1Font.HELVETICA, 12); content.newLineAtOffset(50, 700);
                    content.showText("Inherited font survives edit"); content.endText();
                }
                source.getPages().getCOSObject().setItem(com.tom_roush.pdfbox.cos.COSName.RESOURCES, page.getResources().getCOSObject());
                page.getCOSObject().removeItem(com.tom_roush.pdfbox.cos.COSName.RESOURCES);
                source.save(pdf);
            }
            byte[] edited = PdfProcessor.editPages(context.getContentResolver(), Uri.fromFile(pdf), Collections.singletonList(0), Collections.singletonList(0));
            try (PDDocument output = PDDocument.load(edited)) {
                assertTrue(new com.tom_roush.pdfbox.text.PDFTextStripper().getText(output).contains("Inherited font survives edit"));
            }
        } finally { assertTrue(pdf.delete()); }
    }

    private File image(int width, int height, Bitmap.CompressFormat format, int color) throws Exception {
        File file = File.createTempFile("image-test-", ".image", context.getCacheDir());
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888); bitmap.eraseColor(color);
        try (FileOutputStream output = new FileOutputStream(file)) { assertTrue(bitmap.compress(format, 95, output)); }
        finally { bitmap.recycle(); }
        return file;
    }

    private static final class NonClosingInputStream extends java.io.FilterInputStream {
        NonClosingInputStream(java.io.InputStream input) { super(input); }
        @Override public void close() {}
    }
}
