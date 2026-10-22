package com.fileflow.app.processing;

import java.io.IOException;

public final class DocumentCorners {
    private DocumentCorners() {}

    public static void validate(float[] points) throws IOException {
        if (points == null || points.length != 8) throw new IOException("Choose four document corners.");
        for (float value : points) if (Float.isNaN(value) || Float.isInfinite(value) || value < 0 || value > 100) throw new IOException("Document corner coordinates must be between 0 and 100.");
        for (int index = 0; index < 4; index++) {
            int a = index * 2, b = ((index + 1) % 4) * 2, c = ((index + 2) % 4) * 2;
            float cross = (points[b] - points[a]) * (points[c + 1] - points[b + 1]) - (points[b + 1] - points[a + 1]) * (points[c] - points[b]);
            if (cross <= 1) throw new IOException("Choose corners clockwise: top left, top right, bottom right, bottom left. The page must have four distinct corners.");
        }
    }
}
