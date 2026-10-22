package com.fileflow.app.processing;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.net.Uri;
import com.tom_roush.pdfbox.cos.COSDictionary;
import com.tom_roush.pdfbox.cos.COSName;
import com.tom_roush.pdfbox.io.MemoryUsageSetting;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.pdmodel.PDPage;
import com.tom_roush.pdfbox.pdmodel.PDResources;
import com.tom_roush.pdfbox.pdmodel.graphics.PDXObject;
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject;
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory;
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** Recompresses opaque bitmap assets only; selectable text and page drawing commands are retained. */
public final class LocalPdfCompressor {
    public static final class Result {
        public final byte[] bytes; public final boolean reduced; public final int imagesOptimized;
        Result(byte[] bytes, boolean reduced, int images) { this.bytes = bytes; this.reduced = reduced; imagesOptimized = images; }
    }
    private LocalPdfCompressor() { }
    public static Result compress(ContentResolver resolver, Uri uri, int level) throws IOException { return compress(LocalDocumentProcessor.input(resolver, uri), level); }
    public static Result compress(byte[] source, int level) throws IOException {
        if (level < 0 || level > 2) throw new IOException("Invalid compression level.");
        int[] dimensions = {2400,1800,1200}; float[] quality = {.88f,.75f,.58f};
        byte[] candidate; int count = 0;
        try (PDDocument pdf = PDDocument.load(new java.io.ByteArrayInputStream(source), MemoryUsageSetting.setupTempFileOnly()); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            LocalDocumentProcessor.requireDocument(pdf);
            if (!pdf.getSignatureDictionaries().isEmpty()) throw new IOException("Signed PDFs must be kept unchanged.");
            Set<COSDictionary> visited = Collections.newSetFromMap(new IdentityHashMap<>());
            long deadline = System.nanoTime() + 120_000_000_000L;
            for (PDPage page : pdf.getPages()) count += optimize(pdf, page.getResources(), dimensions[level], quality[level], visited, 0, deadline);
            pdf.save(bytes); candidate = bytes.toByteArray();
        }
        if (candidate.length >= source.length) return new Result(source, false, 0);
        PdfProcessor.validateResult(candidate);
        return new Result(candidate, true, count);
    }
    private static int optimize(PDDocument pdf, PDResources resources, int dimension, float quality, Set<COSDictionary> visited, int depth, long deadline) throws IOException {
        if (resources == null || depth > 20 || !visited.add(resources.getCOSObject())) return 0;
        int count = 0;
        for (COSName name : resources.getXObjectNames()) {
            if (Thread.currentThread().isInterrupted() || System.nanoTime() > deadline) throw new IOException("Local compression timed out. Try fewer pages.");
            PDXObject object = resources.getXObject(name);
            if (object instanceof PDFormXObject) { count += optimize(pdf, ((PDFormXObject) object).getResources(), dimension, quality, visited, depth + 1, deadline); continue; }
            if (!(object instanceof PDImageXObject)) continue;
            PDImageXObject image = (PDImageXObject) object;
            if (image.isStencil() || image.getWidth() < 300 || image.getHeight() < 300 || (long) image.getWidth() * image.getHeight() > 8_000_000 || image.getCOSObject().containsKey(COSName.SMASK) || image.getCOSObject().containsKey(COSName.MASK)) continue;
            Bitmap bitmap = image.getImage(); if (bitmap == null || bitmap.hasAlpha()) continue;
            float scale = Math.min(1, (float) dimension / Math.max(bitmap.getWidth(), bitmap.getHeight()));
            Bitmap prepared = scale < .98f ? Bitmap.createScaledBitmap(bitmap, Math.max(1, Math.round(bitmap.getWidth() * scale)), Math.max(1, Math.round(bitmap.getHeight() * scale)), true) : bitmap;
            try {
                PDImageXObject replacement = JPEGFactory.createFromImage(pdf, prepared, quality);
                if (replacement.getCOSObject().getLength() < image.getCOSObject().getLength()) { resources.put(name, replacement); count++; }
            } finally { if (prepared != bitmap) prepared.recycle(); }
        }
        return count;
    }
}
