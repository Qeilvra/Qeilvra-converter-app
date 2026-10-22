package com.fileflow.server.service;

import com.fileflow.server.config.ProcessingProperties;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.CellStyle;
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
    public record ExtractedTable(String id, int page, List<List<CellValue>> rows, List<String> columnTypes) {
        public ExtractedTable(String id, int page, List<List<CellValue>> rows) { this(id, page, rows, null); }
    }
    public record Response(List<ExtractedTable> tables, int pageCount, boolean scanned) { }
    private final ProcessingProperties properties;

    public TableExtractionService(ProcessingProperties properties) { this.properties = properties; }

    public Response extract(byte[] bytes) {
        try (PDDocument document = PDDocument.load(new ByteArrayInputStream(bytes), MemoryUsageSetting.setupTempFileOnly()); ObjectExtractor extractor = new ObjectExtractor(document)) {
            if (document.isEncrypted()) throw new ProcessingException("Password-protected PDFs are not supported.");
            if (document.getNumberOfPages() > properties.maxPages()) throw new ProcessingException("This PDF exceeds the 500-page processing limit.");
            List<ExtractedTable> result = new ArrayList<>();
            PageIterator pages = extractor.extract();
            int pageNumber = 0;
            int textCells = 0;
            long extractedCells = 0;
            long deadline = System.nanoTime() + properties.timeout().toNanos();
            while (pages.hasNext()) {
                if (System.nanoTime() > deadline || Thread.currentThread().isInterrupted()) throw new ProcessingException(504, "Table extraction exceeded the processing time limit. Split the PDF and try again.");
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
                            String value = column < sourceRow.size() ? sourceRow.get(column).getText() : "";
                            if (++extractedCells > 500_000 || value.length() > 32_767) throw new ProcessingException("The extracted table exceeds the safe review limit. Split the PDF and try again.");
                            if (!value.isEmpty()) textCells++;
                            row.add(new CellValue(value, !lattice && (value.isEmpty() || sourceRow.size() != columns)));
                        }
                        if (row.stream().anyMatch(cell -> !cell.value().isEmpty())) rows.add(row);
                    }
                    if (rows.size() >= 2 && columns >= 2) {
                        if (result.size() >= 100) throw new ProcessingException("This PDF contains more than 100 tables. Split the PDF and try again.");
                        result.add(new ExtractedTable("p" + pageNumber + "-t" + (++tableNumber), pageNumber, rows));
                    }
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
        validateTables(tables);
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new BoundedOutputStream()) {
            CellStyle dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(workbook.createDataFormat().getFormat("yyyy-mm-dd"));
            int index = 0;
            for (ExtractedTable table : tables) {
                Sheet sheet = workbook.createSheet(safeSheetName("Table " + (++index) + " P" + table.page()));
                int rowIndex = 0;
                for (List<CellValue> values : table.rows()) {
                    Row row = sheet.createRow(rowIndex++);
                    for (int column = 0; column < values.size(); column++) {
                        Cell cell = row.createCell(column);
                        String type = rowIndex == 1 || table.columnTypes() == null || column >= table.columnTypes().size()
                                ? "text" : table.columnTypes().get(column);
                        setTypedValue(cell, values.get(column).value(), type, dateStyle);
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

    private static void validateTables(List<ExtractedTable> tables) {
        long cells = 0;
        long characters = 0;
        for (ExtractedTable table : tables) {
            if (table == null || table.rows() == null || table.rows().isEmpty() || table.rows().size() > 1_048_576)
                throw new ProcessingException("Each table must contain valid rows within Excel's limits.");
            if (table.columnTypes() != null && (table.columnTypes().size() > 16_384 || table.columnTypes().stream().anyMatch(type -> type == null || !List.of("text", "number", "date").contains(type))))
                throw new ProcessingException("Column types must be text, number, or date.");
            for (List<CellValue> row : table.rows()) {
                if (row == null || row.size() > 16_384) throw new ProcessingException("A table row exceeds Excel's column limit.");
                cells += row.size();
                for (CellValue cell : row) {
                    if (cell == null || cell.value() == null || cell.value().length() > 32_767)
                        throw new ProcessingException("Each cell must contain text of at most 32,767 characters.");
                    characters += cell.value().length();
                }
                if (cells > 500_000 || characters > 8_000_000) throw new ProcessingException("The reviewed table data exceeds the safe export limit.");
            }
        }
    }

    private static void setTypedValue(Cell cell, String value, String type, CellStyle dateStyle) {
        if (value.isEmpty() || "text".equals(type)) { cell.setCellValue(value); return; }
        if ("number".equals(type)) {
            if (!value.matches("[-+]?(?:0|[1-9]\\d*)(?:\\.\\d+)?"))
                throw new ProcessingException("A Number column contains an identifier or non-decimal value. Choose Text to preserve it.");
            BigDecimal decimal = new BigDecimal(value);
            double number = decimal.doubleValue();
            if (decimal.precision() > 15 || !Double.isFinite(number) || BigDecimal.valueOf(number).compareTo(decimal) != 0)
                throw new ProcessingException("A Number column would lose precision in Excel. Choose Text to preserve it.");
            cell.setCellValue(number);
            return;
        }
        try {
            if (!value.matches("\\d{4}-\\d{2}-\\d{2}")) throw new DateTimeParseException("ISO date required", value, 0);
            LocalDate date = LocalDate.parse(value);
            if (date.getYear() < 1900) throw new DateTimeParseException("Excel date range", value, 0);
            cell.setCellValue(date.atStartOfDay());
            cell.setCellStyle(dateStyle);
        } catch (DateTimeParseException error) {
            throw new ProcessingException("A Date column needs valid YYYY-MM-DD dates from 1900 onward. Choose Text to preserve other values.");
        }
    }

    private static String safeSheetName(String name) { return name.replaceAll("[\\\\/*?:\\[\\]]", "-").substring(0, Math.min(31, name.length())); }
}
