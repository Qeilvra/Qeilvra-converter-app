package com.fileflow.app.processing;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import androidx.annotation.NonNull;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public final class AndroidDocumentProcessor {
    private static final int PAGE_WIDTH = 595;
    private static final int PAGE_HEIGHT = 842;
    private static final int MARGIN = 54;

    private AndroidDocumentProcessor() {}

    public static void textToPdf(@NonNull String text, @NonNull OutputStream output) throws IOException {
        if (text.trim().isEmpty()) throw new IOException("No readable text is available to convert.");
        PdfDocument document = new PdfDocument();
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.BLACK);
        paint.setTextSize(11f);
        paint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL));
        int pageNumber = 0;
        PdfDocument.Page page = null;
        Canvas canvas = null;
        float y = PAGE_HEIGHT;
        try {
            for (String paragraph : text.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1)) {
                List<String> lines = wrap(paragraph, paint, PAGE_WIDTH - MARGIN * 2);
                if (lines.isEmpty()) lines.add("");
                for (String line : lines) {
                    if (page == null || y > PAGE_HEIGHT - MARGIN) {
                        if (page != null) document.finishPage(page);
                        page = document.startPage(new PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, ++pageNumber).create());
                        canvas = page.getCanvas();
                        canvas.drawColor(Color.WHITE);
                        y = MARGIN;
                    }
                    canvas.drawText(line, MARGIN, y, paint);
                    y += 16f;
                }
                y += 5f;
            }
            if (page != null) document.finishPage(page);
            document.writeTo(output);
        } finally {
            document.close();
        }
    }

    private static List<String> wrap(String text, Paint paint, float width) {
        List<String> lines = new ArrayList<>();
        String remaining = text;
        while (!remaining.isEmpty()) {
            int count = paint.breakText(remaining, true, width, null);
            if (count <= 0) break;
            int end = count;
            if (count < remaining.length()) {
                int space = remaining.lastIndexOf(' ', count - 1);
                if (space > 0) end = space;
            }
            lines.add(remaining.substring(0, end));
            remaining = remaining.substring(end).trim();
        }
        return lines;
    }
}
