package com.fileflow.app.processing;

import androidx.annotation.NonNull;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public final class OfficeOpenXml {
    private static final int MAX_DOCUMENT_XML_BYTES = 20_000_000;

    private OfficeOpenXml() {}

    @NonNull
    public static byte[] createXlsxFromTsv(@NonNull String tsv) throws IOException {
        if (tsv.trim().isEmpty()) throw new IOException("The table is empty. Add data before exporting.");
        if (tsv.length() > 4_000_000) throw new IOException("The table exceeds the safe mobile export limit.");
        List<List<List<String>>> tables = TableTsv.parseSheets(tsv);
        List<String> worksheets = new ArrayList<>();
        StringBuilder contentTypes = new StringBuilder();
        StringBuilder workbookSheets = new StringBuilder();
        StringBuilder relationships = new StringBuilder();
        int tableNumber = 0;
        int cellCount = 0;
        for (List<List<String>> lines : tables) {
        tableNumber++;
        contentTypes.append("<Override PartName=\"/xl/worksheets/sheet").append(tableNumber).append(".xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>");
        workbookSheets.append("<sheet name=\"Table ").append(tableNumber).append("\" sheetId=\"").append(tableNumber).append("\" r:id=\"rId").append(tableNumber).append("\"/>");
        relationships.append("<Relationship Id=\"rId").append(tableNumber).append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet").append(tableNumber).append(".xml\"/>");
        StringBuilder sheet = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>");
        int rowNumber = 0;
        for (List<String> line : lines) {
            rowNumber++;
            if (rowNumber > 100_000) throw new IOException("The table exceeds the safe mobile row limit.");
            sheet.append("<row r=\"").append(rowNumber).append("\">");
            String[] cells = line.toArray(new String[0]);
            if (cells.length > 16384 || (cellCount += cells.length) > 250_000) throw new IOException("The table exceeds the safe mobile cell limit.");
            for (int column = 0; column < cells.length; column++) {
                if (cells[column].length() > 32767) throw new IOException("An Excel cell exceeds its 32,767-character limit. Shorten it before exporting.");
                String reference = columnName(column + 1) + rowNumber;
                sheet.append("<c r=\"").append(reference).append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                        .append(escapeXml(cells[column])).append("</t></is></c>");
            }
            sheet.append("</row>");
        }
        sheet.append("</sheetData></worksheet>");
        worksheets.add(sheet.toString());
        }

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            put(zip, "[Content_Types].xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>" + contentTypes + "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/></Types>");
            put(zip, "_rels/.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>");
            put(zip, "xl/workbook.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets>" + workbookSheets + "</sheets></workbook>");
            put(zip, "xl/_rels/workbook.xml.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" + relationships + "<Relationship Id=\"styles\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/></Relationships>");
            put(zip, "xl/styles.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><fonts count=\"1\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font></fonts><fills count=\"2\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill></fills><borders count=\"1\"><border/></borders><cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs><cellXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/></cellXfs></styleSheet>");
            for (int index = 0; index < worksheets.size(); index++) put(zip, "xl/worksheets/sheet" + (index + 1) + ".xml", worksheets.get(index));
        }
        return bytes.toByteArray();
    }

    @NonNull
    public static byte[] createDocx(@NonNull String text) throws IOException {
        StringBuilder body = new StringBuilder();
        String[] paragraphs = text.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        for (String paragraph : paragraphs) {
            body.append("<w:p><w:r><w:t xml:space=\"preserve\">").append(escapeXml(paragraph)).append("</w:t></w:r></w:p>");
        }
        String document = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body>" + body + "<w:sectPr><w:pgSz w:w=\"11906\" w:h=\"16838\"/><w:pgMar w:top=\"1440\" w:right=\"1440\" w:bottom=\"1440\" w:left=\"1440\"/></w:sectPr></w:body></w:document>";
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            put(zip, "[Content_Types].xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/></Types>");
            put(zip, "_rels/.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/></Relationships>");
            put(zip, "word/document.xml", document);
        }
        return bytes.toByteArray();
    }

    @NonNull
    public static String extractDocxText(@NonNull InputStream input) throws Exception {
        byte[] documentXml = null;
        try (ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if ("word/document.xml".equals(entry.getName())) {
                    documentXml = readLimited(zip, MAX_DOCUMENT_XML_BYTES);
                    break;
                }
            }
        }
        if (documentXml == null) throw new IOException("This DOCX has no readable main document.");
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        Document document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(documentXml));
        NodeList paragraphs = document.getElementsByTagNameNS("*", "p");
        List<String> lines = new ArrayList<>();
        for (int index = 0; index < paragraphs.getLength(); index++) {
            Node paragraph = paragraphs.item(index);
            NodeList children = paragraph.getChildNodes();
            StringBuilder line = new StringBuilder();
            appendTextNodes(children, line);
            lines.add(line.toString());
        }
        String result = String.join("\n", lines).trim();
        if (result.isEmpty()) throw new IOException("No readable text was found in this Word document.");
        return result;
    }

    public static void validateDocx(byte[] bytes) throws IOException {
        boolean contentTypes = false;
        boolean document = false;
        int entries = 0;
        long expanded = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > 5000) throw new IOException("The Word result contains too many archive entries.");
                if ("[Content_Types].xml".equals(entry.getName())) contentTypes = true;
                if ("word/document.xml".equals(entry.getName())) document = true;
                int count;
                while ((count = zip.read(buffer)) != -1) {
                    expanded += count;
                    if (expanded > 160L * 1024L * 1024L) throw new IOException("The Word result expands beyond the safe mobile limit.");
                }
            }
        }
        if (!contentTypes || !document) throw new IOException("The processing service did not return a valid Word package. Check the backend URL and retry.");
    }

    private static void appendTextNodes(NodeList nodes, StringBuilder output) {
        for (int index = 0; index < nodes.getLength(); index++) {
            Node node = nodes.item(index);
            if ("t".equals(node.getLocalName())) output.append(node.getTextContent());
            else if ("tab".equals(node.getLocalName())) output.append('\t');
            else if ("br".equals(node.getLocalName())) output.append('\n');
            else appendTextNodes(node.getChildNodes(), output);
        }
    }

    private static byte[] readLimited(InputStream input, int maximum) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        int total = 0;
        while ((count = input.read(buffer)) != -1) {
            total += count;
            if (total > maximum) throw new IOException("This Word document expands beyond the safe processing limit.");
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    private static String columnName(int column) {
        StringBuilder name = new StringBuilder();
        while (column > 0) {
            int remainder = (column - 1) % 26;
            name.insert(0, (char) ('A' + remainder));
            column = (column - 1) / 26;
        }
        return name.toString();
    }

    private static void put(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static String escapeXml(String value) throws IOException {
        StringBuilder escaped = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == '&') escaped.append("&amp;");
            else if (character == '<') escaped.append("&lt;");
            else if (character == '>') escaped.append("&gt;");
            else if (character == '"') escaped.append("&quot;");
            else if (character == '\'') escaped.append("&apos;");
            else if (character == '\r') escaped.append("&#13;");
            else if (Character.isHighSurrogate(character)) {
                if (index + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(index + 1))) throw new IOException("A cell contains an invalid Unicode character. Correct it before exporting.");
                escaped.append(character).append(value.charAt(++index));
            }
            else if (Character.isLowSurrogate(character) || character == 0xfffe || character == 0xffff || (character < 0x20 && character != '\t' && character != '\n')) throw new IOException("A cell contains an unsupported control character. Correct it before exporting.");
            else escaped.append(character);
        }
        return escaped.toString();
    }
}
