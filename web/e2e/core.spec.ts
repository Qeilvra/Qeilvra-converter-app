import { expect, test } from '@playwright/test';
import { Document, Packer, Paragraph } from 'docx';
import ExcelJS from 'exceljs';
import JSZip from 'jszip';
import { PDFDocument, StandardFonts, rgb } from 'pdf-lib';
import { readFile } from 'node:fs/promises';

const pngFixture = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAIAAAD91JpzAAAAFElEQVR4nGP8z8AARAwMjDAGsgAANwQCAfWe2xAAAAAASUVORK5CYII=',
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
  expect(workbook.worksheets[0].getCell('B2').value).toBe('93');
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
  await page.getByRole('button', { name: 'Save' }).click();
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
  await page.locator('input[type=file]').setInputFiles({ name: 'scan.png', mimeType: 'image/png', buffer: pngFixture });
  await page.getByRole('button', { name: 'Crop page 1' }).click();
  await page.locator('.crop-editor input[type=range]').first().fill('5');
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
  await page.getByRole('button', { name: 'Optimize PDF' }).click();
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
  await page.getByRole('button', { name: 'Save' }).click();
  path = await (await pending).path();
  expect(path).not.toBeNull();
  const archive = await JSZip.loadAsync(await readFile(path!));
  expect(Object.keys(archive.files)).toContain('page-1.pdf');
});
