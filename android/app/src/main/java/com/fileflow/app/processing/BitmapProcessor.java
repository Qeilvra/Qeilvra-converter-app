package com.fileflow.app.processing;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
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

    private static Bitmap render(ContentResolver resolver, ImageEdit edit, int maxLongEdge) throws IOException {
        Bitmap source = decode(resolver, edit.uri, maxLongEdge);
        int left = Math.round(source.getWidth() * clamp(edit.cropLeft) / 100f);
        int right = Math.round(source.getWidth() * clamp(edit.cropRight) / 100f);
        int top = Math.round(source.getHeight() * clamp(edit.cropTop) / 100f);
        int bottom = Math.round(source.getHeight() * clamp(edit.cropBottom) / 100f);
        int width = Math.max(1, source.getWidth() - left - right);
        int height = Math.max(1, source.getHeight() - top - bottom);
        Bitmap cropped = Bitmap.createBitmap(source, left, top, width, height);
        if (cropped != source) source.recycle();
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

    private static int clamp(int value) { return Math.max(0, Math.min(45, value)); }
}
