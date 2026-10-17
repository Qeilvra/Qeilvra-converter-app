package com.fileflow.app.processing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

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
