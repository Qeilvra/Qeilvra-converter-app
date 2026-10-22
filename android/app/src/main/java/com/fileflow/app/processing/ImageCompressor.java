package com.fileflow.app.processing;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.net.Uri;
import androidx.annotation.NonNull;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

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
        return compress(resolver, new BitmapProcessor.ImageEdit(uri), targetBytes, requestedQuality, maxWidth, maxHeight);
    }

    @NonNull
    public static Result compress(@NonNull ContentResolver resolver, @NonNull BitmapProcessor.ImageEdit edit, int targetBytes, int requestedQuality, int maxWidth, int maxHeight) throws IOException {
        if (targetBytes < 10 * 1024) throw new IllegalArgumentException("Target must be at least 10 KB");
        if (maxWidth < 1 || maxHeight < 1 || maxWidth > 4096 || maxHeight > 4096) throw new IllegalArgumentException("Maximum dimensions must be between 1 and 4096 pixels");
        Bitmap bitmap = BitmapProcessor.render(resolver, edit, Math.max(maxWidth, maxHeight));
        float scale = Math.min(1f, Math.min((float) maxWidth / bitmap.getWidth(), (float) maxHeight / bitmap.getHeight()));
        if (scale < 1f) {
            Bitmap sized = Bitmap.createScaledBitmap(bitmap, Math.max(1, Math.round(bitmap.getWidth() * scale)), Math.max(1, Math.round(bitmap.getHeight() * scale)), true);
            if (sized != bitmap) bitmap.recycle();
            bitmap = sized;
        }
        if (bitmap.hasAlpha()) {
            Bitmap flattened = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(flattened); canvas.drawColor(Color.WHITE); canvas.drawBitmap(bitmap, 0, 0, null);
            bitmap.recycle(); bitmap = flattened;
        }
        Bitmap working = bitmap;
        try {
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
                    return new Result(bestUnder, width, height, true);
                }
                int nextWidth = Math.round(working.getWidth() * 0.82f);
                int nextHeight = Math.round(working.getHeight() * 0.82f);
                if (Math.max(nextWidth, nextHeight) < MIN_LONG_EDGE) break;
                Bitmap scaled = Bitmap.createScaledBitmap(working, nextWidth, nextHeight, true);
                if (working != bitmap) working.recycle();
                working = scaled;
            }
            if (bestOver == null) throw new IOException("No compressed image could be produced");
            return new Result(bestOver, bestOverWidth, bestOverHeight, false);
        } finally {
            if (working != bitmap && !working.isRecycled()) working.recycle();
            bitmap.recycle();
        }
    }

    private static byte[] encodeJpeg(Bitmap bitmap, int quality) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)) throw new IOException("JPEG encoding failed");
        return output.toByteArray();
    }
}
