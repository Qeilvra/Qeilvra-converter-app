package com.fileflow.app.processing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThrows;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.Test;

public final class OfficeOpenXmlTest {
    @Test
    public void createsValidXlsxPackageWithEditableInlineCells() throws Exception {
        Map<String, String> entries = unzip(OfficeOpenXml.createXlsxFromTsv("Name\tScore\nAyesha\t92"));
        assertTrue(entries.containsKey("[Content_Types].xml"));
        assertTrue(entries.containsKey("xl/workbook.xml"));
        assertTrue(entries.get("xl/worksheets/sheet1.xml").contains("<t xml:space=\"preserve\">Ayesha</t>"));
        assertTrue(entries.get("xl/worksheets/sheet1.xml").contains("r=\"B2\""));
    }

    @Test
    public void createsAndReadsDocxTextWithoutLosingXmlCharacters() throws Exception {
        byte[] docx = OfficeOpenXml.createDocx("One & two\n<editable>");
        assertEquals("One & two\n<editable>", OfficeOpenXml.extractDocxText(new ByteArrayInputStream(docx)));
    }

    @Test public void preservesLeadingZerosLongIdsFormulaLikeTextAndUnicode() throws Exception {
        Map<String, String> entries = unzip(OfficeOpenXml.createXlsxFromTsv("001234\t12345678901234567890\t=SUM(A1:A2)\tاردو"));
        String sheet = entries.get("xl/worksheets/sheet1.xml");
        for (String value : new String[]{"001234", "12345678901234567890", "=SUM(A1:A2)", "اردو"}) assertTrue(sheet.contains(">" + value + "</t>"));
        assertTrue(sheet.contains("t=\"inlineStr\""));
        assertTrue(!sheet.contains("<f>"));
    }

    @Test public void quotedPreviewPreservesEmbeddedWhitespaceAndQuotes() throws Exception {
        String original = "line one\nline two\t\"quoted\"\r";
        String preview = TableTsv.cell(original) + "\t001234";
        assertEquals(original, TableTsv.parse(preview).get(0).get(0));
        String sheet = unzip(OfficeOpenXml.createXlsxFromTsv(preview)).get("xl/worksheets/sheet1.xml");
        assertTrue(sheet.contains("line one\nline two\t&quot;quoted&quot;&#13;"));
    }

    @Test public void rejectsEmptyOversizeCellsAndInvalidQuotedPreview() {
        assertThrows(java.io.IOException.class, () -> OfficeOpenXml.createXlsxFromTsv(""));
        assertThrows(java.io.IOException.class, () -> OfficeOpenXml.createXlsxFromTsv(new String(new char[32768]).replace('\0', 'x')));
        assertThrows(java.io.IOException.class, () -> OfficeOpenXml.createXlsxFromTsv("\"unclosed"));
        assertThrows(java.io.IOException.class, () -> OfficeOpenXml.createXlsxFromTsv("unsupported\u0001"));
        assertThrows(java.io.IOException.class, () -> OfficeOpenXml.createXlsxFromTsv("\uD800"));
    }

    @Test public void multipleTablesKeepTheirSheetsAndLiteralMarkerValues() throws Exception {
        String marker = TableTsv.TABLE_MARKER + "1 ===";
        String preview = marker + "\nID\tValue\n001234\t" + TableTsv.cell(marker) + "\n" + TableTsv.TABLE_MARKER + "2 ===\nName\tAmount\nSecond\t12345678901234567890";
        Map<String, String> entries = unzip(OfficeOpenXml.createXlsxFromTsv(preview));
        assertTrue(entries.containsKey("xl/worksheets/sheet2.xml"));
        assertTrue(entries.get("xl/worksheets/sheet1.xml").contains(marker));
        assertTrue(entries.get("xl/worksheets/sheet2.xml").contains("12345678901234567890"));
        assertTrue(entries.get("xl/workbook.xml").contains("sheetId=\"2\""));
        javax.xml.parsers.DocumentBuilderFactory factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
        for (Map.Entry<String, String> entry : entries.entrySet()) factory.newDocumentBuilder().parse(new ByteArrayInputStream(entry.getValue().getBytes(StandardCharsets.UTF_8)));
    }

    private static Map<String, String> unzip(byte[] bytes) throws Exception {
        Map<String, String> entries = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            byte[] buffer = new byte[4096];
            while ((entry = zip.getNextEntry()) != null) {
                java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
                int count;
                while ((count = zip.read(buffer)) != -1) output.write(buffer, 0, count);
                entries.put(entry.getName(), output.toString(StandardCharsets.UTF_8.name()));
            }
        }
        return entries;
    }
}
