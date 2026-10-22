import { test, expect } from '@playwright/test';
import { PDFDocument, PDFName, PDFString, StandardFonts, rgb } from 'pdf-lib';
import JSZip from 'jszip';
import ExcelJS from 'exceljs';
import { readFile, mkdir, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';

async function fixture() {
  const pdf = await PDFDocument.create();
  const font = await pdf.embedFont(StandardFonts.Helvetica);
  const bold = await pdf.embedFont(StandardFonts.HelveticaBold);
  const png = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVQIHWP4z8DwHwAFgAI/ScLbtAAAAABJRU5ErkJggg==', 'base64');
  const image = await pdf.embedPng(png);
  const rows = [['ID', 'Amount', 'Date'], ['001234', '$1,234.50', '2026-10-22'], ['12345678901234567890', '=SUM(A1:A2)', '03/04/2026']];
  for (let p = 0; p < 2; p++) {
    const page = pdf.addPage(p ? [595,842] : [612,792]);
    page.drawText(`FileFlow offline sample ${p + 1}`, { x: 50, y: 740, size: 18, font: bold });
    page.drawText('This sentence must remain editable in Word.', { x: 50, y: 708, size: 11, font });
    rows.forEach((row, index) => row.forEach((cell, c) => page.drawText(cell, { x: [50,270,420][c], y: 650 - index * 26, size: 11, font: index ? font : bold })));
    page.drawImage(image, { x: 50, y: 450, width: 48, height: 36 });
    page.drawRectangle({ x: 50, y: 400, width: 100, height: 15, color: rgb(.2,.4,.8) }); // Vector artwork is deliberately not recovered.
  }
  return Buffer.from(await pdf.save());
}

test('real offline PDF recovery produces editable DOCX, images, tables and exact XLSX strings', async ({ page }, info) => {
  const api: string[] = []; page.on('request', (request) => { if (request.url().includes('/api/v1')) api.push(request.url()); });
  await page.route('**/api/v1/**', (route) => route.abort());
  const source = await fixture();
  await page.goto('/tools/pdf-to-word');
  await page.locator('input[type=file]').setInputFiles({ name: 'offline-sample.pdf', mimeType: 'application/pdf', buffer: source });
  await page.getByRole('button', { name: 'Convert to DOCX' }).click();
  await expect(page.getByText('Your file is ready')).toBeVisible({ timeout: 30_000 });
  let pending = page.waitForEvent('download'); await page.getByRole('button', { name: 'Save', exact: true }).click();
  const docx = await readFile((await (await pending).path())!); const archive = await JSZip.loadAsync(docx);
  const xml = await archive.file('word/document.xml')!.async('string');
  expect(xml).toContain('This sentence must remain editable');
  expect(xml).toContain('001234'); expect(xml).toContain('12345678901234567890');
  expect((xml.match(/<w:tbl>/g) ?? []).length).toBe(2);
  expect((xml.match(/<w:sectPr>/g) ?? []).length).toBe(2);
  expect(xml).toContain('12240'); expect(xml).toContain('16840'); // Source page dimensions in twips.
  expect(xml).toMatch(/<w:b(?:\s|\/|>)/); expect(xml).toContain('w:val="36"');
  expect(Object.keys(archive.files).filter((name) => /word\/media\/.*\.png/.test(name)).length).toBeGreaterThanOrEqual(1);

  await page.goto('/tools/pdf-to-excel');
  await page.locator('input[type=file]').setInputFiles({ name: 'offline-sample.pdf', mimeType: 'application/pdf', buffer: source });
  await page.getByRole('button', { name: 'Detect tables' }).click();
  await expect(page.getByLabel('Table 1, row 2, column 1')).toHaveValue('001234');
  await expect(page.getByLabel('Table 2, row 3, column 1')).toHaveValue('12345678901234567890');
  await page.getByRole('button', { name: 'Export Excel workbook' }).click();
  pending = page.waitForEvent('download'); await page.getByRole('button', { name: 'Save', exact: true }).click();
  const xlsx = await readFile((await (await pending).path())!); const book = new ExcelJS.Workbook(); await book.xlsx.load(xlsx as never);
  expect(book.worksheets.length).toBe(2);
  for (const sheet of book.worksheets) {
    expect(sheet.getCell('A2').value).toBe('001234'); expect(sheet.getCell('A3').value).toBe('12345678901234567890');
    expect(sheet.getCell('B2').value).toBe('$1,234.50'); expect(sheet.getCell('B3').value).toBe('=SUM(A1:A2)');
    expect(sheet.getCell('C2').value).toBe('2026-10-22'); expect(sheet.getCell('C3').value).toBe('03/04/2026');
  }
  // Enforce safe explicit typing too: long IDs must never pass through a floating-point conversion.
  await page.getByLabel('Table 1, column 1 type').selectOption('number');
  await page.getByRole('button', { name: 'Export Excel workbook' }).click();
  await expect(page.getByRole('alert')).toContainText('cannot be safely converted');
  expect(api).toEqual([]);
  const directory = resolve('../.tools/backend-free-evidence', info.project.name); await mkdir(directory, { recursive: true });
  await Promise.all([writeFile(resolve(directory,'source.pdf'),source),writeFile(resolve(directory,'editable.docx'),docx),writeFile(resolve(directory,'tables.xlsx'),xlsx)]);
  await info.attach('source.pdf', { body: source, contentType: 'application/pdf' });
});

test('scans, mixed PDFs, complex Word files and over-limit documents fail explicitly', async ({ page }) => {
  const scanPng = await page.evaluate(() => {
    const canvas = document.createElement('canvas'); canvas.width = 1200; canvas.height = 1600;
    const context = canvas.getContext('2d')!; context.fillStyle = 'white'; context.fillRect(0,0,1200,1600); context.fillStyle = 'black'; context.font = '40px Arial'; context.fillText('Scanned invoice 001234',80,180); return canvas.toDataURL('image/png').split(',')[1];
  });
  const pdf = await PDFDocument.create(); const scanPage = pdf.addPage(); const raster = await pdf.embedPng(Buffer.from(scanPng,'base64')); scanPage.drawImage(raster,{x:0,y:0,width:595,height:842}); const scan = Buffer.from(await pdf.save());
  await page.goto('/tools/pdf-to-word');
  await page.locator('input[type=file]').setInputFiles({ name: 'scan.pdf', mimeType: 'application/pdf', buffer: scan });
  await page.getByRole('button', { name: 'Convert to DOCX' }).click();
  await expect(page.getByRole('alert')).toContainText('OCR'); await expect(page.getByText('Your file is ready')).toHaveCount(0);
  await page.goto('/tools/pdf-to-excel');
  await page.locator('input[type=file]').setInputFiles({ name: 'scan.pdf', mimeType: 'application/pdf', buffer: scan });
  await page.getByRole('button', { name: 'Detect tables' }).click(); await expect(page.getByRole('alert')).toContainText('scanned');
  const mixed = await PDFDocument.load(await fixture()); const [copiedScan] = await mixed.copyPages(pdf,[0]); mixed.addPage(copiedScan);
  await page.locator('input[type=file]').setInputFiles({ name: 'mixed.pdf', mimeType: 'application/pdf', buffer: Buffer.from(await mixed.save()) });
  await page.getByRole('button', { name: 'Detect tables' }).click(); await expect(page.getByText(/Pages 3 have no selectable text/)).toBeVisible();
  const many = await PDFDocument.create(); for (let i=0; i<101; i++) many.addPage();
  await page.locator('input[type=file]').setInputFiles({ name: 'large.pdf', mimeType: 'application/pdf', buffer: Buffer.from(await many.save()) });
  await page.getByRole('button', { name: 'Detect tables' }).click(); await expect(page.getByRole('alert')).toContainText('100 pages');
  const { Document, Packer, Table, TableRow, TableCell, Paragraph } = await import('docx');
  const complex = await Packer.toBuffer(new Document({ sections:[{children:[new Table({rows:[new TableRow({children:[new TableCell({children:[new Paragraph('Do not omit this table')]})]})]})]}] }));
  await page.goto('/tools/word-to-pdf');
  await page.locator('input[type=file]').setInputFiles({ name:'table.docx',mimeType:'application/vnd.openxmlformats-officedocument.wordprocessingml.document',buffer:complex });
  await page.getByRole('button',{name:'Convert to PDF'}).click(); await expect(page.getByRole('alert')).toContainText('simple text');
});

test('local numeric and date choices are validated and reopen with correct cell types', async ({ page }) => {
  await page.goto('/tools/pdf-to-excel');
  const result = await page.evaluate(async () => {
    const path = '/src/lib/localDocuments.ts'; const engine = await import(path);
    const table = { id:'typed', page:1, columnTypes:['text','number','date'], rows:[['ID','Amount','Date'],['001234','1234.50','2026-10-22']].map(row=>row.map(value=>({value,uncertain:false}))) };
    const blob = await engine.tablesToXlsxLocal([table]);
    let invalid = ''; table.rows[1][2].value = '2026-02-30';
    try { await engine.tablesToXlsxLocal([table]); } catch(error) { invalid = String(error); }
    return { bytes:[...new Uint8Array(await blob.arrayBuffer())], invalid };
  });
  const book = new ExcelJS.Workbook(); await book.xlsx.load(Buffer.from(result.bytes) as never);
  expect(book.worksheets[0].getCell('A2').value).toBe('001234'); expect(book.worksheets[0].getCell('B2').value).toBe(1234.5);
  expect((book.worksheets[0].getCell('C2').value as Date).toISOString()).toBe('2026-10-22T00:00:00.000Z');
  expect(result.invalid).toContain('not a valid date');
});

test('form field data is never silently omitted and signature fields cannot be compressed', async ({ page }) => {
  const formPdf = await PDFDocument.create(); formPdf.addPage();
  const field = formPdf.getForm().createTextField('Identifier'); field.setText('001234'); field.addToPage(formPdf.getPage(0),{x:50,y:600,width:200,height:30});
  await page.goto('/tools/pdf-to-excel');
  await page.locator('input[type=file]').setInputFiles({name:'form.pdf',mimeType:'application/pdf',buffer:Buffer.from(await formPdf.save())});
  await page.getByRole('button',{name:'Detect tables'}).click(); await expect(page.getByRole('alert')).toContainText('form fields');
  const signed = await PDFDocument.create(); signed.addPage();
  const signature = signed.context.register(signed.context.obj({Type:'Annot',Subtype:'Widget',FT:'Sig',T:PDFString.of('Signature'),Rect:[0,0,0,0]}));
  signed.catalog.set(PDFName.of('AcroForm'),signed.context.obj({Fields:[signature]}));
  await page.goto('/tools/compress-pdf');
  await page.locator('input[type=file]').setInputFiles({name:'signature.pdf',mimeType:'application/pdf',buffer:Buffer.from(await signed.save())});
  await page.getByRole('button',{name:'Compress PDF'}).click(); await expect(page.getByRole('alert')).toContainText('Signed PDFs');
  await expect(page.getByText('Your file is ready')).toHaveCount(0);
});
