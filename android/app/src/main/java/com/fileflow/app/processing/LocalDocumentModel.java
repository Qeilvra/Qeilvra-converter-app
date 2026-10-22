package com.fileflow.app.processing;

import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.text.PDFTextStripper;
import com.tom_roush.pdfbox.text.TextPosition;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Positioned glyph extraction, independent of desktop AWT or a server-only Tabula dependency. */
final class LocalDocumentModel {
    static final class Span {
        String text, font; float x, y, width, size;
        Span(TextPosition p) { text = p.getUnicode(); font = p.getFont().getName(); x = p.getXDirAdj(); y = p.getYDirAdj(); width = p.getWidthDirAdj(); size = p.getFontSizeInPt(); }
        Span(String text, float x, float y, float width, float size) { this.text = text; this.x = x; this.y = y; this.width = width; this.size = size; this.font = "Arial"; }
    }
    static final class Line {
        float y, size;
        final List<Span> spans = new ArrayList<>();
        final List<List<Span>> cells = new ArrayList<>();
    }
    static List<Line> read(PDDocument document, int page) throws IOException {
        if (document.getPage(page - 1).getRotation() != 0) throw new IOException("Rotate the PDF page upright with the PDF Editor before local editable conversion.");
        List<Span> spans = new ArrayList<>();
        PDFTextStripper stripper = new PDFTextStripper() {
            int characters;
            @Override protected void processTextPosition(TextPosition position) {
                if (position.getUnicode() == null || position.getUnicode().isEmpty()) return;
                if (++characters > 200_000) throw new IllegalArgumentException("This page exceeds the local text limit.");
                if (position.getDir() != 0) throw new IllegalArgumentException("Rotated or vertical text is not supported by local editable conversion.");
                spans.add(new Span(position));
            }
        };
        stripper.setStartPage(page); stripper.setEndPage(page); stripper.setSortByPosition(true); stripper.getText(document);
        return group(spans);
    }
    static List<Line> group(List<Span> spans) {
        spans.sort(Comparator.comparingDouble((Span s) -> s.y).thenComparingDouble(s -> s.x));
        List<Line> lines = new ArrayList<>();
        for (Span span : spans) {
            Line line = lines.isEmpty() ? null : lines.get(lines.size() - 1);
            if (line == null || Math.abs(line.y - span.y) > Math.max(2, Math.min(line.size, span.size) * .3f)) {
                line = new Line(); line.y = span.y; line.size = span.size; lines.add(line);
            }
            line.spans.add(span); line.size = Math.max(line.size, span.size);
        }
        for (Line line : lines) {
            line.spans.sort(Comparator.comparingDouble(s -> s.x));
            for (Span span : line.spans) {
                List<Span> cell = line.cells.isEmpty() ? null : line.cells.get(line.cells.size() - 1);
                Span previous = cell == null ? null : cell.get(cell.size() - 1);
                if (previous == null || span.x - previous.x - previous.width > Math.max(12, line.size * 1.2f)) {
                    cell = new ArrayList<>(); line.cells.add(cell);
                }
                cell.add(span);
            }
        }
        return lines;
    }
    static String text(List<Span> spans) {
        StringBuilder text = new StringBuilder(); Span previous = null;
        for (Span span : spans) {
            if (previous != null && span.x - previous.x - previous.width > span.size * .15f && !previous.text.endsWith(" ") && !span.text.startsWith(" ")) text.append(' ');
            text.append(span.text); previous = span;
        }
        return text.toString();
    }
    static int tableEnd(List<Line> lines, int start) {
        if (lines.get(start).cells.size() < 2) return start;
        int end = start + 1;
        while (end < lines.size() && lines.get(end).cells.size() >= 2 && lines.get(end).y - lines.get(end - 1).y <= Math.max(45, lines.get(end).size * 3)) end++;
        return end - start >= 2 ? end : start;
    }
    static List<List<List<Span>>> tableRows(List<Line> lines, int start, int end) {
        Line template = lines.get(start);
        for (int i = start + 1; i < end; i++) if (lines.get(i).cells.size() > template.cells.size()) template = lines.get(i);
        List<List<List<Span>>> rows = new ArrayList<>();
        for (int i = start; i < end; i++) {
            List<List<Span>> cells = new ArrayList<>();
            for (int c = 0; c < template.cells.size(); c++) cells.add(new ArrayList<>());
            for (List<Span> cell : lines.get(i).cells) {
                int nearest = 0;
                for (int c = 1; c < template.cells.size(); c++) if (Math.abs(template.cells.get(c).get(0).x - cell.get(0).x) < Math.abs(template.cells.get(nearest).get(0).x - cell.get(0).x)) nearest = c;
                cells.get(nearest).addAll(cell);
            }
            rows.add(cells);
        }
        return rows;
    }
}
