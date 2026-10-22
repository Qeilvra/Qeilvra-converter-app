// Opt-in integration check. Start the local worker and Vite first. No API routes are mocked.
import { chromium } from '@playwright/test';
import { PDFDocument, StandardFonts } from 'pdf-lib';
import ExcelJS from 'exceljs';
import JSZip from 'jszip';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import path from 'node:path';

const origin = new URL(process.env.FILEFLOW_WEB_URL || 'http://127.0.0.1:4173');
if (!['localhost', '127.0.0.1', '[::1]'].includes(origin.hostname) || !['http:', 'https:'].includes(origin.protocol)) throw new Error('This check is restricted to a local development server.');
const base = origin.origin;
const outputDirectory = path.resolve('test-results/processing-live');
await mkdir(outputDirectory, { recursive: true });
const results = [];
const ids = ['001234', '12345678901234567890'];
const pdf = await PDFDocument.create(); const sheet = pdf.addPage([612, 792]);
const font = await pdf.embedFont(StandardFonts.Helvetica);
for (const x of [50, 280, 500]) sheet.drawLine({ start: { x, y: 640 }, end: { x, y: 730 } });
for (const y of [640, 670, 700, 730]) sheet.drawLine({ start: { x: 50, y }, end: { x: 500, y } });
[['ID', 'Amount'], [ids[0], '10.25'], [ids[1], '20.5']].forEach((row, index) => row.forEach((value, column) => sheet.drawText(value, { x: column ? 290 : 60, y: 710 - index * 30, size: 12, font })));
const bytes = Buffer.from(await pdf.save());
const file = { name: 'live-identifiers.pdf', mimeType: 'application/pdf', buffer: bytes };
const healthResponse = await fetch(`${base}/api/v1/health/capabilities`, { signal: AbortSignal.timeout(20_000) });
if (!healthResponse.ok) throw new Error(`Local service health returned ${healthResponse.status}; start the worker and Vite proxy first.`);
const health = await healthResponse.json();
if (!Array.isArray(health.operations)) throw new Error('The local proxy did not return FileFlow capabilities.');

const form = new FormData(); form.append('file', new Blob([bytes], { type: 'application/pdf' }), file.name);
const forbidden = await fetch(`${base}/api/v1/pdf/tables`, { method: 'POST', headers: { Origin: 'https://foreign.invalid' }, body: form, signal: AbortSignal.timeout(20_000) });
if (forbidden.status !== 403) throw new Error(`Foreign Origin was not rejected: ${forbidden.status}.`);
results.push({ feature: 'Foreign-Origin rejection', status: 'PASS', httpStatus: forbidden.status });

const browser = await chromium.launch();
try {
  const page = await browser.newPage();
  page.setDefaultTimeout(30_000);
  if (!health.operations.includes('pdf-tables')) throw new Error('The local table service is unavailable; no table conversion was verified.');
  await page.goto(`${base}/tools/pdf-to-excel`);
  await page.getByText('Processing service available', { exact: true }).waitFor({ timeout: 25_000 });
  await page.locator('input[type=file]').setInputFiles(file);
  await page.getByRole('button', { name: 'Detect tables' }).click();
  for (const [index, id] of ids.entries()) {
    const value = await page.getByLabel(`Table 1, row ${index + 2}, column 1`).inputValue({ timeout: 150_000 });
    if (value !== id) throw new Error(`Table review changed identifier ${index + 1}.`);
  }
  await page.getByRole('button', { name: 'Export Excel workbook' }).click();
  await page.getByText('Your file is ready', { exact: true }).waitFor({ timeout: 150_000 });
  let pending = page.waitForEvent('download'); await page.getByRole('button', { name: 'Save', exact: true }).click();
  const xlsxPath = path.join(outputDirectory, 'identifiers.xlsx'); await (await pending).saveAs(xlsxPath);
  const workbook = new ExcelJS.Workbook(); await workbook.xlsx.readFile(xlsxPath);
  for (const [index, id] of ids.entries()) if (workbook.worksheets[0].getCell(`A${index + 2}`).value !== id) throw new Error('The reopened workbook changed an identifier.');
  results.push({ feature: 'Real PDF table review and XLSX export', status: 'PASS', identifiersPreserved: true });

  if (health.operations.includes('pdf-to-docx')) {
    await page.goto(`${base}/tools/pdf-to-word`);
    await page.getByText('Processing service available', { exact: true }).waitFor({ timeout: 25_000 });
    await page.locator('input[type=file]').setInputFiles(file);
    await page.getByRole('button', { name: 'Convert to DOCX' }).click();
    await page.getByText('Your file is ready', { exact: true }).waitFor({ timeout: 150_000 });
    pending = page.waitForEvent('download'); await page.getByRole('button', { name: 'Save', exact: true }).click();
    const docxPath = path.join(outputDirectory, 'identifiers.docx'); await (await pending).saveAs(docxPath);
    const output = await readFile(docxPath); const archive = await JSZip.loadAsync(output);
    const document = archive.file('word/document.xml');
    if (!document) throw new Error('The downloaded Word archive has no document XML.');
    const xml = await document.async('string'); const text = xml.replace(/<[^>]+>/g, '');
    if (!ids.every((id) => text.includes(id)) || !/<w:t[ >]/.test(xml)) throw new Error('The Word output lost editable text or identifiers.');
    results.push({ feature: 'Real PDF-to-Word download', status: 'PASS', bytes: output.length, editableText: true, identifiersPreserved: true });
  } else results.push({ feature: 'Real PDF-to-Word download', status: 'UNVERIFIED', reason: 'The local PDF-to-DOCX engine is unavailable. Install/configure its documented Python runtime.' });
} finally { await browser.close(); }
const report = { origin: base, fixture: 'synthetic ruled table', mocks: false, results, verifiedAt: new Date().toISOString() };
await writeFile(path.join(outputDirectory, 'report.json'), JSON.stringify(report, null, 2));
console.log(JSON.stringify(report, null, 2));
