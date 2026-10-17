package com.fileflow.app.processing;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.exifinterface.media.ExifInterface;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public final class ImageCompressor {
    private static final int MIN_LONG_EDGE = 320;

    public static final class Result {
        public final byte[] bytes;
        public final int width;
        public final int height;
        public final boolean reachedTarget;

        Result(byte[] bytes, int width, int height, boolean reachedTarget) {
            this.bytes = bytes;
            this.width = width;
            this.height = height;
            this.reachedTarget = reachedTarget;
        }
    }

    private ImageCompressor() {}

    @NonNull
    public static Result compress(
            @NonNull ContentResolver resolver,
            @NonNull Uri uri,
            int targetBytes,
            int requestedQuality,
            int maxLongEdge
    ) throws IOException {
        return compress(resolver, uri, targetBytes, requestedQuality, maxLongEdge, maxLongEdge);
    }

    @NonNull
    public static Result compress(
            @NonNull ContentResolver resolver,
            @NonNull Uri uri,
            int targetBytes,
            int requestedQuality,
            int maxWidth,
            int maxHeight
    ) throws IOException {
        if (targetBytes < 10 * 1024) throw new IllegalArgumentException("Target must be at least 10 KB");
        if (maxWidth < 1 || maxHeight < 1) throw new IllegalArgumentException("Maximum dimensions must be positive");
        Bitmap bitmap = decodeSampled(resolver, uri, maxWidth, maxHeight);
        if (bitmap == null) throw new IOException("Android could not decode this image");
        try {
            Bitmap working = bitmap;
            byte[] bestOver = null;
            int bestOverWidth = working.getWidth();
            int bestOverHeight = working.getHeight();

            for (int dimensionPass = 0; dimensionPass < 8; dimensionPass++) {
                if (Thread.currentThread().isInterrupted()) throw new IOException("Compression was cancelled");
                int low = 8;
                int high = Math.max(10, Math.min(98, requestedQuality));
                byte[] bestUnder = null;
                for (int qualityPass = 0; qualityPass < 8; qualityPass++) {
                    int quality = (low + high) / 2;
                    byte[] candidate = encodeJpeg(working, quality);
                    if (candidate.length <= targetBytes) {
                        if (bestUnder == null || candidate.length > bestUnder.length) bestUnder = candidate;
                        low = quality + 1;
                    } else {
                        if (bestOver == null || candidate.length < bestOver.length) {
                            bestOver = candidate;
                            bestOverWidth = working.getWidth();
                            bestOverHeight = working.getHeight();
                        }
                        high = quality - 1;
                    }
                    if (low > high) break;
                }
                if (bestUnder != null) {
                    int width = working.getWidth();
                    int height = working.getHeight();
                    if (working != bitmap) working.recycle();
                    return new Result(bestUnder, width, height, true);
                }
                int nextWidth = Math.round(working.getWidth() * 0.82f);
                int nextHeight = Math.round(working.getHeight() * 0.82f);
                if (Math.max(nextWidth, nextHeight) < MIN_LONG_EDGE) break;
                Bitmap scaled = Bitmap.createScaledBitmap(working, nextWidth, nextHeight, true);
                if (working != bitmap) working.recycle();
                working = scaled;
            }
            if (working != bitmap) working.recycle();
            if (bestOver == null) throw new IOException("No compressed image could be produced");
            return new Result(bestOver, bestOverWidth, bestOverHeight, false);
        } finally {
            bitmap.recycle();
        }
    }

    private static Bitmap decodeSampled(ContentResolver resolver, Uri uri, int maxWidth, int maxHeight) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream input = resolver.openInputStream(uri)) {
            if (input == null) throw new IOException("The selected file is no longer available");
            BitmapFactory.decodeStream(input, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw new IOException("Unsupported or damaged image");
        int sample = 1;
        int decodeLongEdge = Math.max(maxWidth, maxHeight);
        while (Math.max(bounds.outWidth / sample, bounds.outHeight / sample) > decodeLongEdge * 2) sample *= 2;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        try (InputStream input = resolver.openInputStream(uri)) {
            if (input == null) throw new IOException("The selected file is no longer available");
            Bitmap decoded = BitmapFactory.decodeStream(input, null, options);
            if (decoded == null) return null;
            Bitmap sized = decoded;
            float scale = Math.min(1f, Math.min((float) maxWidth / decoded.getWidth(), (float) maxHeight / decoded.getHeight()));
            if (scale < 1f) {
                sized = Bitmap.createScaledBitmap(decoded, Math.max(1, Math.round(decoded.getWidth() * scale)), Math.max(1, Math.round(decoded.getHeight() * scale)), true);
                if (sized != decoded) decoded.recycle();
            }
            Bitmap oriented = applyOrientation(resolver, uri, sized);
            if (oriented != sized) sized.recycle();
            if (!oriented.hasAlpha()) return oriented;
            Bitmap flattened = Bitmap.createBitmap(oriented.getWidth(), oriented.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(flattened);
            canvas.drawColor(Color.WHITE);
            canvas.drawBitmap(oriented, 0, 0, null);
            oriented.recycle();
            return flattened;
        }
    }

    private static Bitmap applyOrientation(ContentResolver resolver, Uri uri, Bitmap source) {
        try (InputStream input = resolver.openInputStream(uri)) {
            if (input == null) return source;
            int orientation = new ExifInterface(input).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            Matrix matrix = new Matrix();
            switch (orientation) {
                case ExifInterface.ORIENTATION_FLIP_HORIZONTAL: matrix.setScale(-1, 1); break;
                case ExifInterface.ORIENTATION_ROTATE_180: matrix.setRotate(180); break;
                case ExifInterface.ORIENTATION_FLIP_VERTICAL: matrix.setScale(1, -1); break;
                case ExifInterface.ORIENTATION_TRANSPOSE: matrix.setRotate(90); matrix.postScale(-1, 1); break;
                case ExifInterface.ORIENTATION_ROTATE_90: matrix.setRotate(90); break;
                case ExifInterface.ORIENTATION_TRANSVERSE: matrix.setRotate(270); matrix.postScale(-1, 1); break;
                case ExifInterface.ORIENTATION_ROTATE_270: matrix.setRotate(270); break;
                default: return source;
            }
            return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
        } catch (IOException | RuntimeException ignored) {
            return source;
        }
    }

    private static byte[] encodeJpeg(Bitmap bitmap, int quality) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)) throw new IOException("JPEG encoding failed");
        return output.toByteArray();
    }
}
