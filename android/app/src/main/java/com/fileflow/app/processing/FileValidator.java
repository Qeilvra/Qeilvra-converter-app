package com.fileflow.app.processing;

import android.content.ContentResolver;
import android.content.res.AssetFileDescriptor;
import android.net.Uri;
import androidx.annotation.NonNull;
import java.io.IOException;
import java.io.InputStream;

public final class FileValidator {
    public enum FileKind { PDF, DOCX, IMAGE, UNKNOWN }

    private FileValidator() {}

    public static void validateImage(@NonNull ContentResolver resolver, @NonNull Uri uri, long maximumBytes) throws IOException {
        validate(resolver, uri, FileKind.IMAGE, maximumBytes);
    }

    public static long validate(@NonNull ContentResolver resolver, @NonNull Uri uri, @NonNull FileKind expected, long maximumBytes) throws IOException {
        try (AssetFileDescriptor descriptor = resolver.openAssetFileDescriptor(uri, "r")) {
            if (descriptor != null) {
                long length = descriptor.getLength();
                if (length == 0) throw new IOException("This file is empty.");
                if (length > maximumBytes) throw new IOException("This file exceeds the local processing size limit.");
            }
        }
        byte[] header = new byte[12];
        int count;
        long total;
        try (InputStream input = resolver.openInputStream(uri)) {
            if (input == null) throw new IOException("The selected file is unavailable.");
            count = 0;
            while (count < header.length) {
                int next = input.read(header, count, header.length - count);
                if (next < 0) break;
                count += next;
            }
            // Providers can omit or lie about metadata. Enforce the actual stream limit.
            total = count;
            byte[] buffer = new byte[32 * 1024];
            int next;
            while ((next = input.read(buffer)) != -1) {
                total += next;
                if (total > maximumBytes) throw new IOException("This file exceeds the local processing size limit.");
                if (Thread.currentThread().isInterrupted()) throw new IOException("File selection was cancelled.");
            }
            if (total == 0) throw new IOException("This file is empty.");
        }
        FileKind detected = detectHeader(header, count);
        if (expected == FileKind.IMAGE ? detected != FileKind.IMAGE : detected != expected) {
            throw new IOException(expected == FileKind.IMAGE ? "This file is not a supported JPG, PNG or WebP image." : "The file contents do not match the required format.");
        }
        return total;
    }

    public static boolean isSupportedImageHeader(@NonNull byte[] header, int length) {
        if (length >= 3 && (header[0] & 0xff) == 0xff && (header[1] & 0xff) == 0xd8 && (header[2] & 0xff) == 0xff) return true;
        if (length >= 8 && (header[0] & 0xff) == 0x89 && header[1] == 'P' && header[2] == 'N' && header[3] == 'G' && header[4] == 0x0d && header[5] == 0x0a && header[6] == 0x1a && header[7] == 0x0a) return true;
        return length >= 12 && header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F' && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P';
    }

    @NonNull
    public static FileKind detectHeader(@NonNull byte[] header, int length) {
        if (isSupportedImageHeader(header, length)) return FileKind.IMAGE;
        if (length >= 5 && header[0] == '%' && header[1] == 'P' && header[2] == 'D' && header[3] == 'F' && header[4] == '-') return FileKind.PDF;
        if (length >= 4 && header[0] == 'P' && header[1] == 'K' && header[2] == 3 && header[3] == 4) return FileKind.DOCX;
        return FileKind.UNKNOWN;
    }
}
