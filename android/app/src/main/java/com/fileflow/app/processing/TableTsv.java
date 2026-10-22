package com.fileflow.app.processing;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Quoted TSV keeps embedded tabs/newlines and quotes reversible in the editable preview. */
public final class TableTsv {
    public static final String TABLE_MARKER = "=== FileFlow table ";
    private TableTsv() {}

    public static String cell(String value) {
        if (value.startsWith(TABLE_MARKER) || value.indexOf('\t') >= 0 || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0 || value.indexOf('"') >= 0) return "\"" + value.replace("\"", "\"\"") + "\"";
        return value;
    }

    public static List<List<String>> parse(String text) throws IOException {
        List<List<String>> rows = new ArrayList<>();
        for (List<List<String>> sheet : parseSheets(text)) rows.addAll(sheet);
        return rows;
    }

    public static List<List<List<String>>> parseSheets(String text) throws IOException {
        List<List<List<String>>> sheets = new ArrayList<>();
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder value = new StringBuilder();
        boolean quoted = false;
        boolean closed = false;
        int cells = 0;
        for (int index = 0; index < text.length(); index++) {
            if (!quoted && !closed && value.length() == 0 && row.isEmpty() && text.startsWith(TABLE_MARKER, index)) {
                int end = text.indexOf('\n', index);
                if (end < 0) end = text.length();
                String marker = text.substring(index, end).replace("\r", "");
                if (marker.matches("=== FileFlow table [1-9][0-9]* ===")) {
                    if (!rows.isEmpty()) { sheets.add(rows); rows = new ArrayList<>(); }
                    index = end;
                    continue;
                }
            }
            char character = text.charAt(index);
            if (character == '"') {
                if (quoted && index + 1 < text.length() && text.charAt(index + 1) == '"') { value.append('"'); index++; }
                else if (quoted) { quoted = false; closed = true; }
                else if (value.length() == 0 && !closed) quoted = true;
                else throw new IOException("A table quote is misplaced. Use doubled quotes inside quoted cells.");
            } else if (!quoted && (character == '\t' || character == '\n' || character == '\r')) {
                row.add(value.toString()); value.setLength(0); closed = false;
                if (++cells > 250_000 || row.size() > 16384) throw new IOException("The table exceeds the safe mobile cell limit.");
                if (character != '\t') {
                    rows.add(row); row = new ArrayList<>();
                    if (rows.size() > 100_000) throw new IOException("The table exceeds the safe mobile row limit.");
                    if (character == '\r' && index + 1 < text.length() && text.charAt(index + 1) == '\n') index++;
                }
            } else {
                if (closed) throw new IOException("Add a tab or line break after a closing table quote.");
                value.append(character);
                if (value.length() > 32767) throw new IOException("An Excel cell exceeds its 32,767-character limit. Shorten it before exporting.");
            }
        }
        if (quoted) throw new IOException("A table cell has an unclosed quote. Close it before exporting.");
        if (value.length() > 0 || !row.isEmpty() || closed) { row.add(value.toString()); rows.add(row); }
        if (!rows.isEmpty()) sheets.add(rows);
        if (sheets.isEmpty()) throw new IOException("The table is empty. Add data before exporting.");
        if (sheets.size() > 100) throw new IOException("Export at most 100 tables at once.");
        return sheets;
    }
}
