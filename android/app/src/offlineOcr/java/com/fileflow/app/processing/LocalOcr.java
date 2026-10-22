package com.fileflow.app.processing;

import android.graphics.Bitmap;
import android.graphics.Rect;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle;
import com.tom_roush.pdfbox.rendering.PDFRenderer;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Optional build's OCR engine, using the model bundled in the APK. Never a cloud recognition API. */
final class LocalOcr {
    private LocalOcr() { }
    static List<LocalDocumentModel.Line> read(PDDocument pdf, int page) throws IOException {
        PDRectangle bounds = pdf.getPage(page - 1).getCropBox();
        if (pdf.getPage(page - 1).getRotation() != 0) throw new IOException("Rotate the scanned page upright before local OCR.");
        validateImages(pdf.getPage(page - 1).getResources(), java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>()), 0);
        float scale = Math.min(2f, (float) Math.sqrt(4_000_000d / (bounds.getWidth() * bounds.getHeight())));
        if (!Float.isFinite(scale) || scale <= 0 || bounds.getWidth() > 14000 || bounds.getHeight() > 14000) throw new IOException("This scan exceeds the safe OCR page dimensions.");
        Bitmap bitmap = new PDFRenderer(pdf).renderImage(page - 1, scale);
        TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        com.google.android.gms.tasks.Task<Text> task = recognizer.process(InputImage.fromBitmap(bitmap, 0));
        try {
            Text result = Tasks.await(task, 60, TimeUnit.SECONDS);
            List<LocalDocumentModel.Span> spans = new ArrayList<>(); int chars = 0;
            for (Text.TextBlock block : result.getTextBlocks()) for (Text.Line line : block.getLines()) for (Text.Element element : line.getElements()) {
                Rect rect = element.getBoundingBox();
                if (rect == null || element.getText().isEmpty()) continue;
                chars += element.getText().length(); if (chars > 200_000) throw new IOException("OCR text exceeds the local page limit.");
                spans.add(new LocalDocumentModel.Span(element.getText(), rect.left / scale, rect.bottom / scale, rect.width() / scale, Math.max(5, rect.height() / scale)));
            }
            if (spans.isEmpty()) throw new IOException("Offline Latin OCR found no readable text on page " + page + ". Improve the scan or use a supported language.");
            return LocalDocumentModel.group(spans);
        } catch (IOException error) { throw error; }
        catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new IOException("OCR was cancelled.", error); }
        catch (Exception error) { throw new IOException("Offline OCR failed. Try a clearer, smaller scan. " + (error.getMessage() == null ? "" : error.getMessage()), error); }
        finally {
            recognizer.close();
            // A timed-out native task may still own its input. Release pixels after completion.
            if (task.isComplete()) bitmap.recycle();
            else task.addOnCompleteListener(Runnable::run, completed -> bitmap.recycle());
        }
    }
    private static void validateImages(com.tom_roush.pdfbox.pdmodel.PDResources resources, java.util.Set<com.tom_roush.pdfbox.cos.COSDictionary> visited, int depth) throws IOException {
        if (resources == null || !visited.add(resources.getCOSObject())) return;
        if (depth > 20) throw new IOException("Scan resources are nested too deeply.");
        for (com.tom_roush.pdfbox.cos.COSName name : resources.getXObjectNames()) {
            com.tom_roush.pdfbox.pdmodel.graphics.PDXObject object = resources.getXObject(name);
            if (object instanceof com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject) {
                com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject image = (com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject) object;
                if ((long) image.getWidth() * image.getHeight() > 16_000_000) throw new IOException("A scan bitmap exceeds the safe OCR decoding limit.");
            } else if (object instanceof com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject) validateImages(((com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject) object).getResources(), visited, depth + 1);
        }
    }
}
