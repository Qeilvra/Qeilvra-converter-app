package com.fileflow.server.service;

import com.fileflow.server.config.ProcessingProperties;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import technology.tabula.ObjectExtractor;
import technology.tabula.Page;
import technology.tabula.PageIterator;
import technology.tabula.RectangularTextContainer;
import technology.tabula.Table;
import technology.tabula.extractors.BasicExtractionAlgorithm;
import technology.tabula.extractors.SpreadsheetExtractionAlgorithm;

@Service
public class TableExtractionService {
    public record CellValue(String value, boolean uncertain) { }
    public record ExtractedTable(String id, int page, List<List<CellValue>> rows) { }
    public record Response(List<ExtractedTable> tables, int pageCount, boolean scanned) { }
    private final ProcessingProperties properties;

    public TableExtractionService(ProcessingProperties properties) { this.properties = properties; }

    public Response extract(byte[] bytes) {
        try (PDDocument document = PDDocument.load(new ByteArrayInputStream(bytes)); ObjectExtractor extractor = new ObjectExtractor(document)) {
            if (document.isEncrypted()) throw new ProcessingException("Password-protected PDFs are not supported.");
            if (document.getNumberOfPages() > properties.maxPages()) throw new ProcessingException("This PDF exceeds the 500-page processing limit.");
            List<ExtractedTable> result = new ArrayList<>();
            PageIterator pages = extractor.extract();
            int pageNumber = 0;
            int textCells = 0;
            while (pages.hasNext()) {
                Page page = pages.next();
                pageNumber++;
                List<Table> tables = new SpreadsheetExtractionAlgorithm().extract(page);
                boolean lattice = !tables.isEmpty();
                if (tables.isEmpty()) tables = new BasicExtractionAlgorithm().extract(page);
                int tableNumber = 0;
                for (Table table : tables) {
                    List<List<CellValue>> rows = new ArrayList<>();
                    int columns = table.getRows().stream().mapToInt(List::size).max().orElse(0);
                    for (List<RectangularTextContainer> sourceRow : table.getRows()) {
                        List<CellValue> row = new ArrayList<>();
                        for (int column = 0; column < columns; column++) {
                            String value = column < sourceRow.size() ? sourceRow.get(column).getText().trim() : "";
                            if (!value.isEmpty()) textCells++;
                            row.add(new CellValue(value, !lattice && (value.isEmpty() || sourceRow.size() != columns)));
                        }
                        if (row.stream().anyMatch(cell -> !cell.value().isEmpty())) rows.add(row);
                    }
                    if (rows.size() >= 2 && columns >= 2) result.add(new ExtractedTable("p" + pageNumber + "-t" + (++tableNumber), pageNumber, rows));
                }
            }
            return new Response(result, document.getNumberOfPages(), textCells == 0);
        } catch (IOException error) {
            throw new ProcessingException("Table extraction failed for this PDF.", error);
        }
    }

    public byte[] toXlsx(List<ExtractedTable> tables) {
        if (tables == null || tables.isEmpty()) throw new ProcessingException("No reviewed tables are available to export.");
        if (tables.size() > 100) throw new ProcessingException("Export no more than 100 tables at once.");
        long cellCount = tables.stream().flatMap(table -> table.rows().stream()).mapToLong(List::size).sum();
        if (cellCount > 500_000) throw new ProcessingException("The reviewed table data exceeds the safe export limit.");
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            int index = 0;
            for (ExtractedTable table : tables) {
                Sheet sheet = workbook.createSheet(safeSheetName("Table " + (++index) + " P" + table.page()));
                int rowIndex = 0;
                for (List<CellValue> values : table.rows()) {
                    Row row = sheet.createRow(rowIndex++);
                    for (int column = 0; column < values.size(); column++) {
                        Cell cell = row.createCell(column);
                        setTypedValue(cell, values.get(column).value());
                    }
                }
                int maxColumns = table.rows().stream().mapToInt(List::size).max().orElse(0);
                for (int column = 0; column < Math.min(maxColumns, 50); column++) {
                    sheet.autoSizeColumn(column);
                    sheet.setColumnWidth(column, Math.min(sheet.getColumnWidth(column) + 512, 20_000));
                }
            }
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException error) {
            throw new ProcessingException("The Excel workbook could not be generated.", error);
        }
    }

    private static void setTypedValue(Cell cell, String value) {
        String normalized = value.replace(",", "").trim();
        if (normalized.matches("[-+]?\\d+(?:\\.\\d+)?")) {
            try { cell.setCellValue(Double.parseDouble(normalized)); return; } catch (NumberFormatException ignored) { }
        }
        cell.setCellValue(value);
    }

    private static String safeSheetName(String name) { return name.replaceAll("[\\\\/*?:\\[\\]]", "-").substring(0, Math.min(31, name.length())); }
}
