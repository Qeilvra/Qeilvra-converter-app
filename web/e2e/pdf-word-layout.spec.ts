import { test, expect } from '@playwright/test';
import { PDFDocument, StandardFonts, rgb } from 'pdf-lib';
import JSZip from 'jszip';

test('real PDF paint, font families, wrapped paragraphs and semantic lists survive editable Word export', async ({ page }) => {
  const pdf = await PDFDocument.create(); const sheet = pdf.addPage([612, 792]);
  const font = await pdf.embedFont(StandardFonts.TimesRoman);
  const bold = await pdf.embedFont(StandardFonts.TimesRomanBold);
  const red = 'Red segment '; const size = 12;
  sheet.drawText('Document structure', { x: 50, y: 740, size: 20, font: bold });
  sheet.drawText(red, { x: 50, y: 700, size, font, color: rgb(1, 0, 0) });
  sheet.drawText('Blue segment', { x: 50 + font.widthOfTextAtSize(red, size), y: 700, size, font, color: rgb(0, 0, 1) });
  sheet.drawText('A continuation in the same paragraph.', { x: 50, y: 686, size, font });
  sheet.drawText('Underlined text', { x: 50, y: 650, size, font });
  sheet.drawLine({ start: { x: 50, y: 648.5 }, end: { x: 50 + font.widthOfTextAtSize('Underlined text', size), y: 648.5 }, thickness: .6 });
  sheet.drawText('1. Original numbered item', { x: 50, y: 610, size, font });
  await page.goto('/tools/pdf-to-word');
  await page.route('**/api/v1/**', route => route.abort());
  const result = await page.evaluate(async bytes => {
    const modulePath = '/src/lib/localDocuments.ts'; const engine = await import(modulePath);
    const blob = await engine.pdfToDocxLocal(new File([new Uint8Array(bytes)], 'paint.pdf', { type: 'application/pdf' }));
    return [...new Uint8Array(await blob.arrayBuffer())];
  }, [...await pdf.save()]);
  const archive = await JSZip.loadAsync(new Uint8Array(result)); const xml = await archive.file('word/document.xml')!.async('string');
  expect(xml).toContain('w:val="FF0000"'); expect(xml).toContain('w:val="0000FF"');
  expect(xml).toContain('w:ascii="Times New Roman"'); expect(xml).toContain('<w:u ');
  expect(xml).toContain('<w:framePr '); expect(xml).toContain('<w:numPr>');
  expect(xml).toContain('w:pStyle w:val="Heading1"'); expect(xml).not.toContain('<w:tbl>');
  expect((xml.match(/<w:p[ >]/g) ?? []).length).toBeLessThan(7);
  expect(xml).toContain('A continuation in the same paragraph.');
  expect(await archive.file('docProps/custom.xml')!.async('string')).toContain('FileFlowConversionReport');
});
