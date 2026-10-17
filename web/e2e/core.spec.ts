import { expect, test } from '@playwright/test';
import { Document, Packer, Paragraph } from 'docx';
import ExcelJS from 'exceljs';
import JSZip from 'jszip';
import { PDFDocument, StandardFonts, rgb } from 'pdf-lib';
import { readFile } from 'node:fs/promises';

const pngFixture = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAHgAAACgCAYAAADHCaiQAAAAAXNSR0IArs4c6QAAAARnQU1BAACxjwv8YQUAAAAJcEhZcwAADsMAAA7DAcdvqGQAAANlSURBVHhe7dGxjQMBEMPAK92d36cKxMTBw1pwAFbA59VpDj7Owcc5+DgHH+fg43Dw83xsKOLgIxEHH4k4+EjEwUciDj4ScfCRiIOPRL4erP/VHmTEwSPag4w4eER7kBEHj2gPMuLgEe1BRhw8oj3IiINHtAcZcfCI9iAjDh7RHmTEwSPag4w4eER7kBEHj2gPMuLgEe1BRhw8oj3IiINHtAcZcfCI9iAjDh7RHmTEwSPag4w4eER7kBEHj2gPMuLgEe1BRhw8oj3IiINHtAcZcfCI9iAjDh7RHmTEwSPag4w4eER7kBEHj2gPMuLgEe1BRhw8oj3IiINHtAcZcfCI9iAjDh7RHmTEwSPag4w4eER7kBEHj2gPMuLgEe1BRhw8oj3IiINHtAcZcfCI9iAjDh7RHmTEwSPag4w4eER7kBEHj2gPMuLgEe1BRhw8oj3IiINHtAcZcfCI9iAjDh7RHmTEwSPag4w4eER7kBEHj2gPMuLgEe1BRhw8oj3IiINHtAcZcfCI9iAjDh7RHmTEwSPag4w4eER7kBEHj2gPMuLgEe1BRhw8oj3IiINHtAcZcfCI9iAjDh7RHmTEwSPag4w4eER7kBEHj2gPMuLgEe1BRhw8oj3IiINHtAcZcfCI9iAjDh7RHmTEwSPag4w4eER7kBEHj2gPMuLgEe1BRhw8oj3IiINHtAcZcfCI9iAjDh7RHmTEwSPag4w4eER7kBEHj2gPMuLgEe1BRhw8oj3IiINHtAcZcfCI9iAjDh7RHmTEwSPag4w4eER7kBEHj2gPMuLgEe1BRhw8oj3IiINHtAcZcfCI9iAjDh7RHmTEwSPag4w4eER7kBEHj2gPMuLgEe1BRhw8oj3IiINHtAcZcfCI9iAjDh7RHmTEwSPag4w4eER7kBEHj2gPMuLgEe1BRhw8oj3IiINHtAcZcfCI9iAjDh7RHmTEwSPag4w4eER7kBEHj2gPMvL1YPutiIOPRBx8JOLgIxEHH4k4+EjEwUciDj4SwcG6wcHHOfg4Bx/n4OMcfNr7/gECO5BMztNnLAAAAABJRU5ErkJggg==',
  'base64'
);
const scanFixture = Buffer.from(
  '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',
  'base64'
);

async function tablePdf() {
  const pdf = await PDFDocument.create();
  const page = pdf.addPage([612, 792]);
  const font = await pdf.embedFont(StandardFonts.Helvetica);
  const rows = [['Name', 'Score'], ['Ayesha', '92'], ['Bilal', '87']];
  rows.forEach((row, index) => {
    const y = 720 - index * 28;
    page.drawText(row[0], { x: 70, y, size: 13, font, color: rgb(0, 0, 0) });
    page.drawText(row[1], { x: 330, y, size: 13, font, color: rgb(0, 0, 0) });
  });
  return Buffer.from(await pdf.save());
}

async function wordFixture() {
  const document = new Document({ sections: [{ children: [new Paragraph('FileFlow real DOCX fixture'), new Paragraph('Second paragraph')] }] });
  return Buffer.from(await Packer.toBuffer(document));
}

async function mockProcessingApi(page: import('@playwright/test').Page) {
  await page.route('**/api/v1/**', async (route) => {
    const path = new URL(route.request().url()).pathname;
    if (path.endsWith('/pdf/tables/xlsx')) {
      const payload = route.request().postDataJSON() as { tables: Array<{ rows: Array<Array<{ value: string }>> }> };
      const workbook = new ExcelJS.Workbook();
      payload.tables.forEach((table, index) => {
        const sheet = workbook.addWorksheet(`Table ${index + 1}`);
        table.rows.forEach((row) => sheet.addRow(row.map((cell) => /^\d+(?:\.\d+)?$/.test(cell.value) ? Number(cell.value) : cell.value)));
      });
      await route.fulfill({ status: 200, contentType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', body: Buffer.from(await workbook.xlsx.writeBuffer()) });
      return;
    }
    if (path.endsWith('/pdf/tables')) {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ pageCount: 1, scanned: false, tables: [{ id: 'p1-t1', page: 1, rows: [[{ value: 'Name', uncertain: false }, { value: 'Score', uncertain: false }], [{ value: 'Ayesha', uncertain: false }, { value: '92', uncertain: false }], [{ value: 'Bilal', uncertain: false }, { value: '87', uncertain: false }]] }] }) });
      return;
    }
    if (path.endsWith('/convert/docx-to-pdf')) {
      await route.fulfill({ status: 200, contentType: 'application/pdf', body: await tablePdf() });
      return;
    }
    if (path.endsWith('/pdf/compress')) {
      const compressed = await PDFDocument.create(); compressed.addPage([200, 200]);
      await route.fulfill({ status: 200, contentType: 'application/pdf', headers: { 'Access-Control-Expose-Headers': 'X-FileFlow-Reduced,X-FileFlow-Images-Optimized', 'X-FileFlow-Reduced': 'true', 'X-FileFlow-Images-Optimized': '1' }, body: Buffer.from(await compressed.save()) });
      return;
    }
    await route.fulfill({ status: 404, contentType: 'application/json', body: JSON.stringify({ error: 'Unmocked processing route' }) });
  });
}

test.beforeEach(async ({ page }) => mockProcessingApi(page));

test('dashboard routes every tool and remains within a mobile viewport', async ({ page }) => {
  await page.goto('/');
  await expect(page.getByRole('heading', { name: /Convert, compress/i })).toBeVisible();
  let overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
  expect(overflow).toBeLessThanOrEqual(1);
  await page.goto('/tools');
  for (const route of ['pdf-to-excel', 'pdf-to-word', 'word-to-pdf', 'image-to-pdf', 'image-converter', 'compress-image', 'compress-pdf', 'scanner', 'pdf-editor', 'prepare-upload']) {
    await expect(page.locator(`a[href="/tools/${route}"]`).first()).toBeAttached();
  }
  overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
  expect(overflow).toBeLessThanOrEqual(1);
});

test('PDF to Excel accepts a real PDF, exposes editable cells, and downloads a valid XLSX', async ({ page }) => {
  await page.goto('/tools/pdf-to-excel');
  await page.locator('input[type=file]').setInputFiles({ name: 'scores.pdf', mimeType: 'application/pdf', buffer: await tablePdf() });
  await page.getByRole('button', { name: 'Detect tables' }).click();
  const score = page.getByLabel('Table 1, row 2, column 2');
  await expect(score).toHaveValue('92', { timeout: 20_000 });
  await score.fill('93');
  await page.getByRole('button', { name: 'Export Excel workbook' }).click();
  await expect(page.getByText('Your file is ready')).toBeVisible();
  const downloadPromise = page.waitForEvent('download');
  await page.getByRole('button', { name: 'Save' }).click();
  const download = await downloadPromise;
  const path = await download.path();
  expect(path).not.toBeNull();
  const workbook = new ExcelJS.Workbook();
  await workbook.xlsx.readFile(path!);
  expect(workbook.worksheets[0].getCell('B2').value).toBe(93);
});

test('Word to PDF and image conversion produce genuine downloadable formats', async ({ page }) => {
  await page.goto('/tools/word-to-pdf');
  await page.locator('input[type=file]').setInputFiles({
    name: 'fixture.docx',
    mimeType: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
    buffer: await wordFixture()
  });
  await page.getByRole('button', { name: 'Convert to PDF' }).click();
  await expect(page.getByText('Your file is ready')).toBeVisible();
  let pending = page.waitForEvent('download');
  await page.getByRole('button', { name: 'Save', exact: true }).click();
  let downloaded = await pending;
  let filePath = await downloaded.path();
  expect(filePath).not.toBeNull();
  const convertedPdf = await PDFDocument.load(await readFile(filePath!));
  expect(convertedPdf.getPageCount()).toBeGreaterThan(0);

  await page.goto('/tools/image-converter');
  await page.locator('input[type=file]').setInputFiles({ name: 'tiny.png', mimeType: 'image/png', buffer: pngFixture });
  await page.getByRole('button', { name: 'Convert image' }).click();
  await expect(page.getByText('Your file is ready')).toBeVisible();
  pending = page.waitForEvent('download');
  await page.getByRole('button', { name: 'Save' }).click();
  downloaded = await pending;
  filePath = await downloaded.path();
  expect(filePath).not.toBeNull();
  const jpeg = await readFile(filePath!);
  expect([...jpeg.subarray(0, 3)]).toEqual([0xff, 0xd8, 0xff]);
});

test('image PDF, scanner crop, compression, and upload preparation complete end to end', async ({ page }) => {
  await page.goto('/tools/image-to-pdf');
  await page.locator('input[type=file]').setInputFiles([
    { name: 'first.png', mimeType: 'image/png', buffer: pngFixture },
    { name: 'second.png', mimeType: 'image/png', buffer: pngFixture }
  ]);
  await page.getByRole('button', { name: 'Create PDF' }).click();
  await expect(page.getByText('Your file is ready')).toBeVisible();
  let pending = page.waitForEvent('download');
  await page.getByRole('button', { name: 'Save' }).click();
  let path = await (await pending).path();
  expect(path).not.toBeNull();
  expect((await PDFDocument.load(await readFile(path!))).getPageCount()).toBe(2);

  await page.goto('/tools/scanner');
  await page.locator('input[type=file]').setInputFiles({ name: 'scan.jpg', mimeType: 'image/jpeg', buffer: scanFixture });
  await page.getByRole('button', { name: 'Adjust corners page 1' }).click();
  await expect(page.getByRole('button', { name: 'Move topLeft corner' })).toBeVisible();
  await page.getByRole('button', { name: 'Create PDF' }).click();
  await expect(page.getByText('Your file is ready')).toBeVisible();

  await page.goto('/tools/compress-image');
  await page.locator('input[type=file]').setInputFiles({ name: 'compress.png', mimeType: 'image/png', buffer: pngFixture });
  await page.getByRole('button', { name: 'Compress image', exact: true }).click();
  await expect(page.getByText('Your file is ready')).toBeVisible();

  await page.goto('/tools/prepare-upload');
  await page.locator('input[type=file]').setInputFiles({ name: 'profile.png', mimeType: 'image/png', buffer: pngFixture });
  await page.getByRole('button', { name: 'Prepare file' }).click();
  await expect(page.getByText('Your file is ready')).toBeVisible();
});

test('PDF compression, merge, page editing, and split generate parseable outputs', async ({ page }) => {
  const first = { name: 'first.pdf', mimeType: 'application/pdf', buffer: await tablePdf() };
  const second = { name: 'second.pdf', mimeType: 'application/pdf', buffer: await tablePdf() };
  await page.goto('/tools/compress-pdf');
  await page.locator('input[type=file]').setInputFiles(first);
  await page.getByRole('button', { name: 'Compress PDF' }).click();
  await expect(page.getByText('Your file is ready')).toBeVisible();
  let pending = page.waitForEvent('download');
  await page.getByRole('button', { name: 'Save', exact: true }).click();
  let path = await (await pending).path();
  expect(path).not.toBeNull();
  expect((await PDFDocument.load(await readFile(path!))).getPageCount()).toBe(1);

  await page.goto('/tools/pdf-editor');
  await page.locator('input[type=file]').setInputFiles([first, second]);
  await page.getByRole('button', { name: 'Merge PDFs' }).click();
  await expect(page.getByText('Your file is ready')).toBeVisible();
  pending = page.waitForEvent('download');
  await page.getByRole('button', { name: 'Save' }).click();
  path = await (await pending).path();
  expect(path).not.toBeNull();
  expect((await PDFDocument.load(await readFile(path!))).getPageCount()).toBe(2);

  await page.locator('input[type=file]').setInputFiles(first);
  await expect(page.getByRole('button', { name: 'Split pages to ZIP' })).toBeVisible({ timeout: 20_000 });
  await page.getByRole('button', { name: 'Rotate page 1' }).click();
  await page.getByRole('button', { name: 'Save edited PDF' }).click();
  await expect(page.getByText('Your file is ready')).toBeVisible();
  await page.getByRole('button', { name: 'Split pages to ZIP' }).click();
  await expect(page.getByText('first-pages.zip')).toBeVisible();
  pending = page.waitForEvent('download');
  await page.getByRole('button', { name: 'Save', exact: true }).click();
  path = await (await pending).path();
  expect(path).not.toBeNull();
  const archive = await JSZip.loadAsync(await readFile(path!));
  expect(Object.keys(archive.files)).toContain('page-1.pdf');
});
