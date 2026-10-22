import { afterEach, describe, expect, it, vi } from 'vitest';
import { PDFDocument } from 'pdf-lib';
import ExcelJS from 'exceljs';
import JSZip from 'jszip';
import { checkProcessingService, compressPdfRemote, convertDocument, extractPdfTablesRemote, tablesToXlsxRemote } from './processingApi';

const source = new File(['%PDF-source'], 'input.pdf', { type: 'application/pdf' });
const respond = (blob: Blob, headers = new Headers()) => ({ ok: true, status: 200, blob: async () => blob, headers }) as Response;
afterEach(() => { vi.unstubAllGlobals(); vi.useRealTimers(); });

describe('processing client validation and failures', () => {
  it('accepts a real workbook response without altering text identifiers', async () => {
    const workbook = new ExcelJS.Workbook(); const sheet = workbook.addWorksheet('Identifiers');
    sheet.addRow(['001234', '12345678901234567890']);
    vi.stubGlobal('fetch', vi.fn(async () => respond(new Blob([await workbook.xlsx.writeBuffer() as BlobPart]))));
    const output = await tablesToXlsxRemote([{ id: 'ids', page: 1, rows: [[{ value: '001234', uncertain: false }, { value: '12345678901234567890', uncertain: false }]] }]);
    const parsed = new ExcelJS.Workbook(); await parsed.xlsx.load(await output.arrayBuffer());
    expect(parsed.worksheets[0].getCell('A1').value).toBe('001234');
    expect(parsed.worksheets[0].getCell('B1').value).toBe('12345678901234567890');
  });
  it('reports a missing deployment endpoint with configuration guidance', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => ({ ok: false, status: 404, json: async () => { throw new Error('HTML'); } })));
    await expect(convertDocument(source, 'pdf-word')).rejects.toThrow(/hosted FileFlow backend/);
  });
  it('rejects HTTP 200 HTML and empty Office responses instead of reporting success', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => respond(new Blob(['<html>SPA fallback</html>']))));
    await expect(convertDocument(source, 'pdf-word')).rejects.toThrow(/valid DOCX/);
    vi.stubGlobal('fetch', vi.fn(async () => respond(new Blob([]))));
    await expect(tablesToXlsxRemote([])).rejects.toThrow(/empty file/);
  });
  it('rejects Office archives with corrupt XML instead of accepting nonempty entry names', async () => {
    const zip = new JSZip();
    zip.file('[Content_Types].xml', '<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"/>');
    zip.file('word/document.xml', 'garbage');
    vi.stubGlobal('fetch', vi.fn(async () => respond(new Blob([await zip.generateAsync({ type: 'uint8array' }) as BlobPart]))));
    await expect(convertDocument(source, 'pdf-word')).rejects.toThrow(/valid DOCX/);
  });
  it('parses actual PDF output and uses measured size rather than a reduction header', async () => {
    const pdf = await PDFDocument.create(); pdf.addPage();
    const blob = new Blob([await pdf.save() as BlobPart], { type: 'application/pdf' });
    vi.stubGlobal('fetch', vi.fn(async () => respond(blob, new Headers({ 'X-FileFlow-Reduced': 'true' }))));
    const output = await compressPdfRemote(source, 'BALANCED');
    expect(output.reduced).toBe(false);
    expect((await PDFDocument.load(await output.blob.arrayBuffer())).getPageCount()).toBe(1);
  });
  it('rejects malformed table service responses', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => respond(new Blob([JSON.stringify({ tables: [null], scanned: false, pageCount: 1 })]))));
    await expect(extractPdfTablesRemote(source)).rejects.toThrow(/invalid table data/);
  });
  it('checks health response structure and times out unreachable health requests', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => respond(new Blob([JSON.stringify({ status: 'ready' })]))));
    await expect(checkProcessingService()).rejects.toThrow(/service health/);
    vi.useFakeTimers();
    vi.stubGlobal('fetch', vi.fn((_url, init: RequestInit) => new Promise((_resolve, reject) => init.signal?.addEventListener('abort', () => reject(new DOMException('Aborted', 'AbortError'))))));
    const checked = checkProcessingService();
    const assertion = expect(checked).rejects.toThrow(/timed out/);
    await vi.advanceTimersByTimeAsync(20_001);
    await assertion;
  });
  it('rejects oversized responses from Content-Length before reading a download', async () => {
    const body = respond(new Blob(['not read']), new Headers({ 'Content-Length': String(101 * 1024 * 1024) }));
    const read = vi.spyOn(body, 'blob');
    vi.stubGlobal('fetch', vi.fn(async () => body));
    await expect(convertDocument(source, 'pdf-word')).rejects.toThrow(/download limit/);
    expect(read).not.toHaveBeenCalled();
  });
});
