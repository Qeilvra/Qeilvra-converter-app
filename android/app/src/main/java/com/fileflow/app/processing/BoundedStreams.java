package com.fileflow.app.processing;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public final class BoundedStreams {
    private BoundedStreams() {}

    public static long copy(InputStream input, OutputStream output, long maximum) throws IOException {
        byte[] buffer = new byte[32 * 1024];
        long total = 0;
        int count;
        while ((count = input.read(buffer)) != -1) {
            if (Thread.currentThread().isInterrupted()) throw new IOException("Processing was cancelled.");
            total += count;
            if (total > maximum) throw new IOException("The file exceeds the safe processing size limit.");
            output.write(buffer, 0, count);
        }
        return total;
    }

    public static byte[] read(InputStream input, int maximum) throws IOException {
        if (input == null) return new byte[0];
        try (InputStream stream = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            copy(stream, output, maximum);
            return output.toByteArray();
        }
    }
}
