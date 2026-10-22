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
