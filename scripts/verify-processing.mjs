// Real HTTP integration checks. No routed/mocked conversion responses.
// Run: node scripts/verify-processing.mjs http://127.0.0.1:8080/api/v1 [--require-engines]
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
const require = createRequire(new URL('../web/package.json', import.meta.url));
const { PDFDocument, StandardFonts } = require('pdf-lib');
const { Document, Packer, Paragraph } = require('docx');
const ExcelJS = require('exceljs');
const JSZip = require('jszip');
const base = (process.argv.slice(2).find(argument => !argument.startsWith('--')) ?? 'http://127.0.0.1:8080/api/v1').replace(/\/$/, '');
const report = [];
let failures = 0;
let unverified = 0;
const requireEngines = process.argv.includes('--require-engines');
async function check(name, fn) {
  const start = performance.now();
  try {
    const detail = await fn();
    const result = detail?.conversion?.startsWith('UNVERIFIED') ? 'UNVERIFIED' : 'PASS';
    if (result === 'UNVERIFIED') unverified++;
    report.push({ name, result, milliseconds: Math.round(performance.now() - start), detail });
  } catch (error) {
    failures++;
    report.push({ name, result: 'FAIL', error: error.message });
  }
}
async function request(path, options = {}) {
  return fetch(`${base}${path}`, { ...options, signal: AbortSignal.timeout(150_000) });
}
async function upload(path, bytes, name, type) {
  const body = new FormData();
  body.append('file', new Blob([bytes], { type }), name);
  return request(path, { method: 'POST', body });
}
async function accepted(response) {
  assert.equal(response.status, 200, `HTTP ${response.status}: ${(await response.clone().text()).slice(0, 300)}`);
  const bytes = Buffer.from(await response.arrayBuffer());
  assert.ok(bytes.length > 0, 'Empty output');
  assert.match(response.headers.get('cache-control') ?? '', /no-store/);
  return bytes;
}
async function tableFixture() {
  const pdf = await PDFDocument.create();
  const page = pdf.addPage([612, 792]);
  const font = await pdf.embedFont(StandardFonts.Helvetica);
  const rows = [['ID', 'Value'], ['001234', '92'], ['12345678901234567890', '87']];
  for (let r = 0; r <= rows.length; r++) page.drawLine({ start: { x: 40, y: 730 - r * 35 }, end: { x: 560, y: 730 - r * 35 } });
  for (const x of [40, 340, 560]) page.drawLine({ start: { x, y: 730 }, end: { x, y: 625 } });
  rows.forEach((row, r) => row.forEach((value, c) => page.drawText(value, { x: 50 + c * 300, y: 707 - r * 35, size: 12, font })));
  return Buffer.from(await pdf.save());
}
const source = await tableFixture();
let capabilities;
let extracted;
await check('Actual service capabilities', async () => {
  const response = await request('/health/capabilities');
  assert.equal(response.status, 200);
  assert.match(response.headers.get('content-type') ?? '', /application\/json/);
  capabilities = await response.json();
  assert.ok(Array.isArray(capabilities.operations), 'Not a FileFlow capabilities response');
  return capabilities;
});
await check('Real ruled-table extraction preserves source values', async () => {
  extracted = JSON.parse((await accepted(await upload('/pdf/tables', source, 'identifiers.pdf', 'application/pdf'))).toString());
  assert.equal(extracted.scanned, false);
  assert.equal(extracted.pageCount, 1);
  const values = extracted.tables.flatMap(t => t.rows.flatMap(r => r.map(c => c.value)));
  for (const value of ['001234', '12345678901234567890', '92']) assert.ok(values.includes(value), `Missing source value ${value}`);
  return { tables: extracted.tables.length, values };
});
await check('Real XLSX reopens and preserves leading-zero/long identifiers', async () => {
  assert.ok(extracted, 'Extraction prerequisite failed');
  const bytes = await accepted(await request('/pdf/tables/xlsx', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(extracted) }));
  const workbook = new ExcelJS.Workbook();
  await workbook.xlsx.load(bytes);
  assert.equal(workbook.worksheets[0].getCell('A2').value, '001234');
  assert.equal(workbook.worksheets[0].getCell('A3').value, '12345678901234567890');
  return { worksheets: workbook.worksheets.length, bytes: bytes.length };
});
await check('Explicit number/date export creates valid typed cells', async () => {
  const tables = [{ id: 'typed', page: 1, columnTypes: ['text', 'number', 'date'], rows: [
    ['ID', 'Amount', 'Date'].map(value => ({ value, uncertain: false })),
    ['001234', '92.50', '2026-10-22'].map(value => ({ value, uncertain: false }))
  ] }];
  const response = await request('/pdf/tables/xlsx', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ tables }) });
  const workbook = new ExcelJS.Workbook();
  await workbook.xlsx.load(await accepted(response));
  assert.equal(workbook.worksheets[0].getCell('A2').value, '001234');
  assert.equal(workbook.worksheets[0].getCell('B2').value, 92.5);
  assert.equal(workbook.worksheets[0].getCell('C2').value.toISOString().slice(0, 10), '2026-10-22');
});
await check('Unsafe explicit numeric identifier rejected', async () => {
  const tables = [{ id: 'unsafe', page: 1, columnTypes: ['number'], rows: [[{ value: 'ID' }], [{ value: '12345678901234567890' }]] }];
  const response = await request('/pdf/tables/xlsx', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ tables }) });
  assert.equal(response.status, 422);
});
await check('Real PDF compression retains pages and truthful size flag', async () => {
  const response = await upload('/pdf/compress', source, 'table.pdf', 'application/pdf');
  const bytes = await accepted(response);
  assert.equal((await PDFDocument.load(bytes)).getPageCount(), 1);
  assert.ok(bytes.length <= source.length);
  assert.equal(response.headers.get('x-fileflow-reduced'), String(bytes.length < source.length));
  return { inputBytes: source.length, outputBytes: bytes.length };
});
await check('Malformed PDF rejected', async () => {
  const response = await upload('/pdf/tables', Buffer.from('%PDF-1.7\ninvalid'), 'bad.pdf', 'application/pdf');
  assert.equal(response.status, 422);
});
await check('Unapproved browser origin rejected', async () => {
  const response = await request('/health/capabilities', { headers: { Origin: 'https://unapproved.example' } });
  assert.equal(response.status, 403);
});
await check('501-page PDF rejected', async () => {
  const pdf = await PDFDocument.create();
  for (let i = 0; i < 501; i++) pdf.addPage([72, 72]);
  const response = await upload('/pdf/tables', await pdf.save(), 'too-many-pages.pdf', 'application/pdf');
  assert.equal(response.status, 422);
});
await check('Supported 500-page PDF parsed without invented OCR data', async () => {
  const pdf = await PDFDocument.create();
  for (let i = 0; i < 500; i++) pdf.addPage([72, 72]);
  const response = JSON.parse((await accepted(await upload('/pdf/tables', await pdf.save(), '500-pages.pdf', 'application/pdf'))).toString());
  assert.equal(response.pageCount, 500);
  assert.equal(response.scanned, true);
  assert.equal(response.tables.length, 0);
  return { note: 'Blank page-count boundary fixture; not a complex 50 MB workload benchmark' };
});
await check('Image-only PDF-to-Word refuses screenshot-only success', async () => {
  const pdf = await PDFDocument.create();
  const png = await pdf.embedPng(Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jJ1sAAAAASUVORK5CYII=', 'base64'));
  pdf.addPage([72, 72]).drawImage(png, { x: 0, y: 0, width: 72, height: 72 });
  const response = await upload('/convert/pdf-to-docx', await pdf.save(), 'scan.pdf', 'application/pdf');
  assert.equal(response.status, 422);
  assert.match((await response.json()).error, /OCR/);
});
await check('Malformed reviewed-table payload rejected without server error', async () => {
  const response = await request('/pdf/tables/xlsx', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: '{"tables":[null]}' });
  assert.equal(response.status, 422);
});
// Engine calls are intentionally made even when health is degraded: they must fail
// clearly with a non-success status, never return fake converted files.
await check('PDF-to-DOCX actual engine or explicit unavailable response', async () => {
  const response = await upload('/convert/pdf-to-docx', source, 'table.pdf', 'application/pdf');
  if (response.status === 503) {
    const body = await response.json();
    assert.ok(body.error);
    return { conversion: 'UNVERIFIED: engine unavailable', error: body.error };
  }
  const bytes = await accepted(response);
  const zip = await JSZip.loadAsync(bytes);
  const xml = await zip.file('word/document.xml')?.async('string');
  assert.ok(xml?.includes('001234'), 'DOCX does not preserve editable source identifier');
  assert.ok(xml.includes('12345678901234567890'));
  return { conversion: 'verified source text in DOCX', bytes: bytes.length };
});
await check('DOCX-to-PDF actual engine or explicit unavailable response', async () => {
  const docx = await Packer.toBuffer(new Document({ sections: [{ children: [new Paragraph('FileFlow live conversion fixture')] }] }));
  const response = await upload('/convert/docx-to-pdf', docx, 'sample.docx', 'application/vnd.openxmlformats-officedocument.wordprocessingml.document');
  if (response.status === 503) {
    const body = await response.json();
    assert.ok(body.error);
    return { conversion: 'UNVERIFIED: engine unavailable', error: body.error };
  }
  const bytes = await accepted(response);
  assert.ok((await PDFDocument.load(bytes)).getPageCount() > 0);
  return { conversion: 'verified parseable PDF', bytes: bytes.length };
});
console.log(JSON.stringify({ base, checkedAt: new Date().toISOString(), failures, unverified, requireEngines, checks: report }, null, 2));
process.exitCode = failures || (requireEngines && unverified) ? 1 : 0;
