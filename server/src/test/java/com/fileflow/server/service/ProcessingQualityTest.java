package com.fileflow.server.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fileflow.server.config.ProcessingProperties;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.time.Duration;
import javax.imageio.ImageIO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.model.XWPFHeaderFooterPolicy;
import org.apache.poi.xwpf.usermodel.Document;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.Test;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblWidth;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTblWidth;

class ProcessingQualityTest {
    private final ProcessingProperties properties = new ProcessingProperties(
            50L * 1024 * 1024, 500, Duration.ofSeconds(5), 1, "http://localhost",
            "soffice", "fileflow-qpdf-not-installed", "python3 -m pdf2docx.main convert {input} {output}");

    @Test
    void imageHeavyPdfIsReducedWithoutDestroyingSelectableText() throws Exception {
        byte[] source = imageHeavyPdf();
        PdfCompressionService service = new PdfCompressionService(properties, new ProcessRunner(properties));
        PdfCompressionService.Result result = service.compress(source, PdfCompressionService.Level.HIGH);

        assertTrue(result.reduced());
        assertTrue(result.bytes().length < source.length);
        assertTrue(result.imagesOptimized() > 0);
        try (PDDocument document = PDDocument.load(result.bytes())) {
            assertEquals(1, document.getNumberOfPages());
            assertTrue(new PDFTextStripper().getText(document).contains("FileFlow searchable text"));
        }
    }

    @Test
    void ruledPdfTableBecomesValidWorkbookPreservingSourceText() throws Exception {
        TableExtractionService service = new TableExtractionService(properties);
        TableExtractionService.Response response = service.extract(ruledTablePdf());

        assertFalse(response.scanned());
        assertFalse(response.tables().isEmpty());
        byte[] workbookBytes = service.toXlsx(response.tables());
        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(workbookBytes))) {
            assertTrue(workbook.getNumberOfSheets() > 0);
            assertEquals("Name", workbook.getSheetAt(0).getRow(0).getCell(0).getStringCellValue());
            assertEquals("92", workbook.getSheetAt(0).getRow(1).getCell(1).getStringCellValue());
        }
    }

    @Test
    void acceptanceDocxContainsFormattingTablesImagesHeadersFootersAndUrdu() throws Exception {
        byte[] bytes = complexDocx();
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            assertTrue(document.getParagraphs().stream().anyMatch(p -> p.getText().contains("FileFlow Acceptance Document")));
            assertTrue(document.getParagraphs().stream().anyMatch(p -> p.getText().contains("اردو متن")));
            assertEquals(1, document.getTables().size());
            assertEquals(2, document.getAllPictures().size());
            assertFalse(document.getHeaderList().isEmpty());
            assertFalse(document.getFooterList().isEmpty());
        }
    }

    static byte[] complexDocx() throws Exception {
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.getDocument().getBody().addNewSectPr().addNewPgMar().setLeft(BigInteger.valueOf(1080));
            XWPFHeaderFooterPolicy policy = document.createHeaderFooterPolicy();
            policy.createHeader(XWPFHeaderFooterPolicy.DEFAULT).createParagraph().createRun().setText("FileFlow quality fixture");
            XWPFParagraph footer = policy.createFooter(XWPFHeaderFooterPolicy.DEFAULT).createParagraph();
            footer.setAlignment(ParagraphAlignment.CENTER);
            footer.createRun().setText("Page ");
            footer.getCTP().addNewFldSimple().setInstr("PAGE");

            XWPFParagraph title = document.createParagraph();
            title.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun titleRun = title.createRun();
            titleRun.setText("FileFlow Acceptance Document");
            titleRun.setBold(true);
            titleRun.setFontFamily("Arial");
            titleRun.setFontSize(24);
            titleRun.setColor("1769E0");

            XWPFParagraph multilingual = document.createParagraph();
            XWPFRun english = multilingual.createRun();
            english.setText("English paragraph with styled text. ");
            english.setItalic(true);
            XWPFRun urdu = multilingual.createRun();
            urdu.setText("اردو متن اور دستاویز");
            urdu.setFontFamily("Noto Nastaliq Urdu");
            urdu.setFontSize(16);

            XWPFTable table = document.createTable(4, 3);
            table.getRow(0).getCell(0).setText("Item");
            table.getRow(0).getCell(1).setText("Quantity");
            table.getRow(0).getCell(2).setText("Amount");
            table.getRow(1).getCell(0).setText("Consulting");
            table.getRow(1).getCell(1).setText("2");
            table.getRow(1).getCell(2).setText("1,200.00");
            table.getRow(2).getCell(0).setText("Design");
            table.getRow(2).getCell(1).setText("1");
            table.getRow(2).getCell(2).setText("850.00");
            table.getRow(3).getCell(0).setText("Merged summary");
            table.getRow(3).getCell(0).getCTTc().addNewTcPr().addNewGridSpan().setVal(BigInteger.valueOf(2));
            table.getRow(3).removeCell(1);
            table.getRow(3).getCell(1).setText("2,050.00");
            CTTblWidth width = table.getCTTbl().getTblPr().addNewTblW();
            width.setType(STTblWidth.DXA);
            width.setW(BigInteger.valueOf(9000));

            byte[] firstImage = samplePng(new Color(23, 105, 224), "LOGO");
            byte[] secondImage = samplePng(new Color(15, 147, 96), "CHART");
            XWPFParagraph images = document.createParagraph();
            images.createRun().addPicture(new ByteArrayInputStream(firstImage), Document.PICTURE_TYPE_PNG, "logo.png", Units.toEMU(120), Units.toEMU(60));
            images.createRun().addPicture(new ByteArrayInputStream(secondImage), Document.PICTURE_TYPE_PNG, "chart.png", Units.toEMU(120), Units.toEMU(60));
            document.createParagraph().createRun().addBreak(org.apache.poi.xwpf.usermodel.BreakType.PAGE);
            document.createParagraph().createRun().setText("Second page content for pagination verification.");
            document.write(output);
            return output.toByteArray();
        }
    }

    private static byte[] imageHeavyPdf() throws Exception {
        BufferedImage image = new BufferedImage(1800, 1800, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                image.setRGB(x, y, new Color((x * 31 + y * 17) & 255, (x * 7 + y * 29) & 255, (x * 13 + y * 3) & 255).getRGB());
            }
        }
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            PDImageXObject embedded = JPEGFactory.createFromImage(document, image, 1f, 300);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.drawImage(embedded, 20, 120, 555, 555);
                content.beginText();
                content.setFont(PDType1Font.HELVETICA, 12);
                content.newLineAtOffset(40, 80);
                content.showText("FileFlow searchable text");
                content.endText();
            }
            document.save(output);
            return output.toByteArray();
        }
    }

    private static byte[] ruledTablePdf() throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                float left = 70, bottom = 620, rowHeight = 35, width = 360;
                for (int row = 0; row <= 3; row++) {
                    content.moveTo(left, bottom + row * rowHeight); content.lineTo(left + width, bottom + row * rowHeight);
                }
                for (float x : new float[] {left, left + 240, left + width}) {
                    content.moveTo(x, bottom); content.lineTo(x, bottom + 3 * rowHeight);
                }
                content.stroke();
                String[][] values = {{"Name", "Score"}, {"Ayesha", "92"}, {"Bilal", "87"}};
                for (int row = 0; row < values.length; row++) {
                    for (int column = 0; column < 2; column++) {
                        content.beginText();
                        content.setFont(PDType1Font.HELVETICA, 12);
                        content.newLineAtOffset(left + 10 + column * 240, bottom + (2 - row) * rowHeight + 12);
                        content.showText(values[row][column]);
                        content.endText();
                    }
                }
            }
            document.save(output);
            return output.toByteArray();
        }
    }

    private static byte[] samplePng(Color color, String label) throws Exception {
        BufferedImage image = new BufferedImage(320, 160, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.WHITE); graphics.fillRect(0, 0, 320, 160);
        graphics.setColor(color); graphics.fillRoundRect(10, 10, 300, 140, 24, 24);
        graphics.setColor(Color.WHITE); graphics.drawString(label, 135, 85);
        graphics.dispose();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }
}
