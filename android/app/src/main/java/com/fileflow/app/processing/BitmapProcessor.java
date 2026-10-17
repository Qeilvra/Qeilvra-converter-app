package com.fileflow.app.processing;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.exifinterface.media.ExifInterface;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public final class BitmapProcessor {
    public static class ImageEdit {
        @NonNull public final Uri uri;
        public int rotation;
        public int cropTop;
        public int cropRight;
        public int cropBottom;
        public int cropLeft;
        public boolean perspective;
        public float topLeftX = 4, topLeftY = 4, topRightX = 96, topRightY = 4;
        public float bottomRightX = 96, bottomRightY = 96, bottomLeftX = 4, bottomLeftY = 96;
        public int scanFilter;
        public ImageEdit(@NonNull Uri uri) { this.uri = uri; }
    }

    private BitmapProcessor() {}

    @NonNull
    public static byte[] convert(@NonNull ContentResolver resolver, @NonNull ImageEdit edit, @NonNull String format, int quality, int maxLongEdge) throws IOException {
        Bitmap bitmap = render(resolver, edit, maxLongEdge);
        try {
            Bitmap.CompressFormat outputFormat;
            if ("png".equals(format)) outputFormat = Bitmap.CompressFormat.PNG;
            else if ("webp".equals(format)) outputFormat = Build.VERSION.SDK_INT >= 30 ? Bitmap.CompressFormat.WEBP_LOSSY : Bitmap.CompressFormat.WEBP;
            else outputFormat = Bitmap.CompressFormat.JPEG;
            Bitmap encoded = bitmap;
            if (outputFormat == Bitmap.CompressFormat.JPEG && bitmap.hasAlpha()) encoded = flatten(bitmap);
            try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                if (!encoded.compress(outputFormat, Math.max(10, Math.min(100, quality)), output)) throw new IOException("Image encoding failed.");
                return output.toByteArray();
            } finally {
                if (encoded != bitmap) encoded.recycle();
            }
        } finally {
            bitmap.recycle();
        }
    }

    @NonNull
    public static byte[] imagesToPdf(@NonNull ContentResolver resolver, @NonNull List<ImageEdit> edits) throws IOException {
        if (edits.isEmpty()) throw new IOException("Add at least one image.");
        if (edits.size() > 50) throw new IOException("A PDF can contain at most 50 images.");
        PdfDocument document = new PdfDocument();
        try {
            int pageNumber = 0;
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            for (ImageEdit edit : edits) {
                if (Thread.currentThread().isInterrupted()) throw new IOException("PDF creation was cancelled.");
                Bitmap bitmap = render(resolver, edit, 2600);
                try {
                    boolean landscape = bitmap.getWidth() > bitmap.getHeight();
                    int pageWidth = landscape ? 842 : 595;
                    int pageHeight = landscape ? 595 : 842;
                    PdfDocument.Page page = document.startPage(new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, ++pageNumber).create());
                    Canvas canvas = page.getCanvas();
                    canvas.drawColor(Color.WHITE);
                    int margin = 28;
                    float scale = Math.min((pageWidth - margin * 2f) / bitmap.getWidth(), (pageHeight - margin * 2f) / bitmap.getHeight());
                    int width = Math.round(bitmap.getWidth() * scale);
                    int height = Math.round(bitmap.getHeight() * scale);
                    int left = (pageWidth - width) / 2;
                    int top = (pageHeight - height) / 2;
                    canvas.drawBitmap(bitmap, null, new Rect(left, top, left + width, top + height), paint);
                    document.finishPage(page);
                } finally {
                    bitmap.recycle();
                }
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            document.writeTo(output);
            return output.toByteArray();
        } finally {
            document.close();
        }
    }

    @NonNull
    public static Bitmap thumbnail(@NonNull ContentResolver resolver, @NonNull Uri uri) throws IOException {
        return decode(resolver, uri, 160);
    }

    @NonNull
    public static float[] detectDocumentCorners(@NonNull ContentResolver resolver, @NonNull Uri uri) throws IOException {
        Bitmap bitmap = decode(resolver, uri, 900);
        try {
            int width = bitmap.getWidth(), height = bitmap.getHeight();
            int[] pixels = new int[width * height]; bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
            float[] gray = new float[pixels.length];
            for (int index = 0; index < pixels.length; index++) gray[index] = Color.red(pixels[index]) * .299f + Color.green(pixels[index]) * .587f + Color.blue(pixels[index]) * .114f;
            double sum = 0, squared = 0; int samples = 0;
            float[] edges = new float[pixels.length];
            for (int y = 1; y < height - 1; y++) for (int x = 1; x < width - 1; x++) {
                int i = y * width + x;
                float gx = -gray[i-width-1] + gray[i-width+1] - 2*gray[i-1] + 2*gray[i+1] - gray[i+width-1] + gray[i+width+1];
                float gy = -gray[i-width-1] - 2*gray[i-width] - gray[i-width+1] + gray[i+width-1] + 2*gray[i+width] + gray[i+width+1];
                float value = (float) Math.hypot(gx, gy); edges[i] = value; sum += value; squared += value * value; samples++;
            }
            double mean = sum / Math.max(1, samples); double threshold = mean + 1.2 * Math.sqrt(Math.max(0, squared / Math.max(1, samples) - mean * mean));
            float[] result = {4,4,96,4,96,96,4,96};
            double minSum = Double.MAX_VALUE, maxSum = -Double.MAX_VALUE, maxDiff = -Double.MAX_VALUE, minDiff = Double.MAX_VALUE; int strong = 0;
            for (int y = Math.max(1, height/40); y < height-height/40; y++) for (int x = Math.max(1, width/40); x < width-width/40; x++) {
                if (edges[y*width+x] < threshold) continue; strong++;
                double sumScore=x+y, diff=x-y;
                if (sumScore < minSum) { minSum=sumScore; result[0]=x*100f/width; result[1]=y*100f/height; }
                if (diff > maxDiff) { maxDiff=diff; result[2]=x*100f/width; result[3]=y*100f/height; }
                if (sumScore > maxSum) { maxSum=sumScore; result[4]=x*100f/width; result[5]=y*100f/height; }
                if (diff < minDiff) { minDiff=diff; result[6]=x*100f/width; result[7]=y*100f/height; }
            }
            return strong < Math.max(80, width*height/500) ? new float[] {4,4,96,4,96,96,4,96} : result;
        } finally { bitmap.recycle(); }
    }

    private static Bitmap render(ContentResolver resolver, ImageEdit edit, int maxLongEdge) throws IOException {
        Bitmap source = decode(resolver, edit.uri, maxLongEdge);
        if (edit.perspective) source = perspective(source, edit);
        int left = Math.round(source.getWidth() * clamp(edit.cropLeft) / 100f);
        int right = Math.round(source.getWidth() * clamp(edit.cropRight) / 100f);
        int top = Math.round(source.getHeight() * clamp(edit.cropTop) / 100f);
        int bottom = Math.round(source.getHeight() * clamp(edit.cropBottom) / 100f);
        int width = Math.max(1, source.getWidth() - left - right);
        int height = Math.max(1, source.getHeight() - top - bottom);
        Bitmap cropped = Bitmap.createBitmap(source, left, top, width, height);
        if (cropped != source) source.recycle();
        if (edit.scanFilter != 0) {
            Bitmap enhanced = enhance(cropped, edit.scanFilter);
            if (enhanced != cropped) cropped.recycle();
            cropped = enhanced;
        }
        if (edit.rotation % 360 == 0) return cropped;
        Matrix matrix = new Matrix();
        matrix.setRotate(edit.rotation % 360);
        Bitmap rotated = Bitmap.createBitmap(cropped, 0, 0, cropped.getWidth(), cropped.getHeight(), matrix, true);
        if (rotated != cropped) cropped.recycle();
        return rotated;
    }

    private static Bitmap decode(ContentResolver resolver, Uri uri, int maxLongEdge) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream input = resolver.openInputStream(uri)) {
            if (input == null) throw new IOException("The selected image is unavailable.");
            BitmapFactory.decodeStream(input, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw new IOException("This image is damaged or unsupported.");
        int sample = 1;
        while (Math.max(bounds.outWidth / sample, bounds.outHeight / sample) > maxLongEdge * 2) sample *= 2;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap decoded;
        try (InputStream input = resolver.openInputStream(uri)) {
            if (input == null) throw new IOException("The selected image is unavailable.");
            decoded = BitmapFactory.decodeStream(input, null, options);
        }
        if (decoded == null) throw new IOException("This image could not be decoded.");
        int longEdge = Math.max(decoded.getWidth(), decoded.getHeight());
        Bitmap sized = decoded;
        if (longEdge > maxLongEdge) {
            float scale = maxLongEdge / (float) longEdge;
            sized = Bitmap.createScaledBitmap(decoded, Math.max(1, Math.round(decoded.getWidth() * scale)), Math.max(1, Math.round(decoded.getHeight() * scale)), true);
            if (sized != decoded) decoded.recycle();
        }
        return orient(resolver, uri, sized);
    }

    private static Bitmap orient(ContentResolver resolver, Uri uri, Bitmap source) {
        try (InputStream input = resolver.openInputStream(uri)) {
            if (input == null) return source;
            int orientation = new ExifInterface(input).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            Matrix matrix = new Matrix();
            if (orientation == ExifInterface.ORIENTATION_ROTATE_90) matrix.setRotate(90);
            else if (orientation == ExifInterface.ORIENTATION_ROTATE_180) matrix.setRotate(180);
            else if (orientation == ExifInterface.ORIENTATION_ROTATE_270) matrix.setRotate(270);
            else if (orientation == ExifInterface.ORIENTATION_FLIP_HORIZONTAL) matrix.setScale(-1, 1);
            else if (orientation == ExifInterface.ORIENTATION_FLIP_VERTICAL) matrix.setScale(1, -1);
            else return source;
            Bitmap oriented = Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
            if (oriented != source) source.recycle();
            return oriented;
        } catch (IOException | RuntimeException ignored) { return source; }
    }

    private static Bitmap flatten(Bitmap source) {
        Bitmap output = Bitmap.createBitmap(source.getWidth(), source.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        canvas.drawColor(Color.WHITE);
        canvas.drawBitmap(source, 0, 0, null);
        return output;
    }

    private static Bitmap perspective(Bitmap source, ImageEdit edit) throws IOException {
        float[] src = {source.getWidth()*edit.topLeftX/100f, source.getHeight()*edit.topLeftY/100f, source.getWidth()*edit.topRightX/100f, source.getHeight()*edit.topRightY/100f, source.getWidth()*edit.bottomRightX/100f, source.getHeight()*edit.bottomRightY/100f, source.getWidth()*edit.bottomLeftX/100f, source.getHeight()*edit.bottomLeftY/100f};
        int width = Math.round(Math.max((float)Math.hypot(src[2]-src[0], src[3]-src[1]), (float)Math.hypot(src[4]-src[6], src[5]-src[7])));
        int height = Math.round(Math.max((float)Math.hypot(src[6]-src[0], src[7]-src[1]), (float)Math.hypot(src[4]-src[2], src[5]-src[3])));
        if (width < 20 || height < 20) throw new IOException("The four document corners do not form a valid page.");
        float[] dst = {0,0,width,0,width,height,0,height}; Matrix matrix = new Matrix();
        if (!matrix.setPolyToPoly(src, 0, dst, 0, 4)) throw new IOException("Perspective correction failed for this page.");
        Bitmap output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888); Canvas canvas = new Canvas(output); canvas.drawColor(Color.WHITE); canvas.drawBitmap(source, matrix, new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG)); source.recycle(); return output;
    }

    private static Bitmap enhance(Bitmap source, int filter) {
        Bitmap output = Bitmap.createBitmap(source.getWidth(), source.getHeight(), Bitmap.Config.ARGB_8888);
        if (filter == 1) {
            ColorMatrix matrix = new ColorMatrix(); matrix.setSaturation(0); Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG); paint.setColorFilter(new ColorMatrixColorFilter(matrix)); new Canvas(output).drawBitmap(source, 0, 0, paint); return output;
        }
        int[] pixels = new int[source.getWidth()*source.getHeight()]; source.getPixels(pixels,0,source.getWidth(),0,0,source.getWidth(),source.getHeight());
        for (int i=0;i<pixels.length;i++) { int gray=Math.round(Color.red(pixels[i])*.299f+Color.green(pixels[i])*.587f+Color.blue(pixels[i])*.114f); int value=gray>155?255:0; pixels[i]=Color.rgb(value,value,value); }
        output.setPixels(pixels,0,source.getWidth(),0,0,source.getWidth(),source.getHeight()); return output;
    }

    private static int clamp(int value) { return Math.max(0, Math.min(45, value)); }
}
