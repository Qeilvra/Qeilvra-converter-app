package com.fileflow.server.service;

import static org.junit.jupiter.api.Assertions.*;
import com.fileflow.server.config.ProcessingProperties;
import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;

class TableIntegrityTest {
    private final TableExtractionService service = new TableExtractionService(new ProcessingProperties(
            50_000_000, 500, Duration.ofSeconds(20), 1, "http://localhost", "soffice", "qpdf", "pdf2docx {input} {output}"));

    private static List<TableExtractionService.CellValue> row(String... values) {
        return Arrays.stream(values).map(value -> new TableExtractionService.CellValue(value, false)).toList();
    }

    @Test void preservesIdentifiersCurrencyDatesUnicodeAndFormulaLikeText() throws Exception {
        List<String> values = List.of("001234", "123456789012345678901234", "1,200.00", "01/02/2026", "=SUM(A1:A2)", "\u0627\u0631\u062f\u0648", "  spaced  ", "");
        var table = new TableExtractionService.ExtractedTable("one", 1, List.of(row(values.toArray(String[]::new))));
        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(service.toXlsx(List.of(table))))) {
            for (int index = 0; index < values.size(); index++) {
                var cell = workbook.getSheetAt(0).getRow(0).getCell(index);
                assertEquals(CellType.STRING, cell.getCellType());
                assertEquals(values.get(index), cell.getStringCellValue());
            }
        }
    }

    @Test void explicitNumbersAndIsoDatesRemainTypedWithTextHeadersAndSeparateSheets() throws Exception {
        var table = new TableExtractionService.ExtractedTable("one", 2,
                List.of(row("Identifier", "Amount", "Date"), row("001234", "1200.50", "2026-10-22")), List.of("text", "number", "date"));
        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(service.toXlsx(List.of(table, table))))) {
            assertEquals(2, workbook.getNumberOfSheets());
            var sheet = workbook.getSheetAt(0);
            assertEquals("Amount", sheet.getRow(0).getCell(1).getStringCellValue());
            assertEquals("001234", sheet.getRow(1).getCell(0).getStringCellValue());
            assertEquals(1200.5, sheet.getRow(1).getCell(1).getNumericCellValue());
            assertEquals("2026-10-22", sheet.getRow(1).getCell(2).getLocalDateTimeCellValue().toLocalDate().toString());
            assertEquals("yyyy-mm-dd", sheet.getRow(1).getCell(2).getCellStyle().getDataFormatString());
        }
    }

    @Test void rejectsLossyExplicitNumbersAndInvalidDates() {
        for (String value : List.of("001234", "1234567890123456", "1,200.50", "1e20", " 12 ")) {
            var table = new TableExtractionService.ExtractedTable("one", 1, List.of(row("Header"), row(value)), List.of("number"));
            assertThrows(ProcessingException.class, () -> service.toXlsx(List.of(table)), value);
        }
        for (String value : List.of("2026-02-30", "10/22/2026", "1899-01-01", "2026-1-2")) {
            var table = new TableExtractionService.ExtractedTable("one", 1, List.of(row("Date"), row(value)), List.of("date"));
            assertThrows(ProcessingException.class, () -> service.toXlsx(List.of(table)), value);
        }
    }

    @Test void rejectsMalformedAndOversizedCellsWithoutNpe() {
        assertThrows(ProcessingException.class, () -> service.toXlsx(null));
        assertThrows(ProcessingException.class, () -> service.toXlsx(Arrays.asList((TableExtractionService.ExtractedTable) null)));
        assertThrows(ProcessingException.class, () -> service.toXlsx(List.of(new TableExtractionService.ExtractedTable("one", 1, null))));
        assertThrows(ProcessingException.class, () -> service.toXlsx(List.of(new TableExtractionService.ExtractedTable("one", 1, List.of(row("x".repeat(32_768)))))));
        assertThrows(ProcessingException.class, () -> service.toXlsx(List.of(new TableExtractionService.ExtractedTable("one", 1, List.of(row("x")), Arrays.asList((String)null)))));
    }
}
