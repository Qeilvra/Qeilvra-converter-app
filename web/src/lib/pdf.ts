import ExcelJS from 'exceljs';
import JSZip from 'jszip';
import { degrees, PDFDocument } from 'pdf-lib';
import type { ExtractedTable } from '../types';

type PdfJs = typeof import('pdfjs-dist');

let pdfJsPromise: Promise<PdfJs> | null = null;
const loadPdfJs = async (): Promise<PdfJs> => {
  if (!pdfJsPromise) {
    const useLegacyBuild = typeof navigator !== 'undefined' && /jsdom/i.test(navigator.userAgent);
    pdfJsPromise = (async () => {
      if (useLegacyBuild) {
        const [pdfjs, worker] = await Promise.all([
          import('pdfjs-dist/legacy/build/pdf.mjs'),
          import('pdfjs-dist/legacy/build/pdf.worker.mjs')
        ]);
        (globalThis as typeof globalThis & { pdfjsWorker?: unknown }).pdfjsWorker = worker;
        return pdfjs;
      }
      const pdfjs = await import('pdfjs-dist');
      pdfjs.GlobalWorkerOptions.workerSrc = new URL('pdfjs-dist/build/pdf.worker.min.mjs', import.meta.url).toString();
      return pdfjs;
    })();
  }
  return pdfJsPromise;
};

const readPdf = async (file: File) => {
  const pdfjs = await loadPdfJs();
  const bytes = new Uint8Array(await file.arrayBuffer());
  try {
    return await pdfjs.getDocument({ data: bytes }).promise;
  } catch (error) {
    const message = error instanceof Error ? error.message : '';
    if (/password/i.test(message)) throw new Error('This PDF is password-protected. Remove the password before processing.');
    throw new Error(message ? `This PDF could not be opened: ${message}` : 'This PDF could not be opened. It may be damaged or unsupported.');
  }
};

type PositionedText = { str: string; x: number; y: number; width: number };

export const extractPdfTables = async (file: File): Promise<{ tables: ExtractedTable[]; pageCount: number; scanned: boolean }> => {
  const pdf = await readPdf(file);
  try {
    if (pdf.numPages > 500) throw new Error('This PDF exceeds the 500-page browser processing limit. Split it into smaller files first.');
    const tables: ExtractedTable[] = [];
    let textItems = 0;
    for (let pageNumber = 1; pageNumber <= pdf.numPages; pageNumber += 1) {
      const page = await pdf.getPage(pageNumber);
      const content = await page.getTextContent();
      const items: PositionedText[] = content.items
        .filter((item): item is typeof item & { str: string; transform: number[]; width: number } => 'str' in item && item.str.trim().length > 0)
        .map((item) => ({ str: item.str.trim(), x: item.transform[4], y: item.transform[5], width: item.width }));
      textItems += items.length;
      const lineMap: PositionedText[][] = [];
      for (const item of items.sort((a, b) => b.y - a.y || a.x - b.x)) {
        const line = lineMap.find((candidate) => Math.abs(candidate[0].y - item.y) <= 4);
        if (line) line.push(item);
        else lineMap.push([item]);
      }
      const rows = lineMap
        .map((line) => line.sort((a, b) => a.x - b.x))
        .filter((line) => line.length >= 2)
        .map((line) => line.map((item) => ({ value: item.str, uncertain: false })));
      if (rows.length) {
        const counts = rows.map((row) => row.length);
        const expected = [...counts].sort((a, b) => a - b)[Math.floor(counts.length / 2)];
        rows.forEach((row) => row.forEach((cell) => { cell.uncertain = row.length !== expected; }));
        tables.push({ id: crypto.randomUUID(), page: pageNumber, rows });
      }
    }
    return { tables, pageCount: pdf.numPages, scanned: textItems === 0 };
  } finally {
    await pdf.cleanup();
  }
};

export const tablesToXlsx = async (tables: ExtractedTable[]): Promise<Blob> => {
  if (tables.length === 0) throw new Error('No reviewed tables are available to export.');
  const workbook = new ExcelJS.Workbook();
  workbook.creator = 'FileFlow';
  workbook.created = new Date();
  tables.forEach((table, index) => {
    const sheet = workbook.addWorksheet(`Table ${index + 1} P${table.page}`.slice(0, 31));
    table.rows.forEach((row) => sheet.addRow(row.map((cell) => cell.value)));
    sheet.columns.forEach((column) => {
      const values = column.values ?? [];
      column.width = Math.min(45, Math.max(12, ...values.map((value) => String(value ?? '').length + 2)));
    });
  });
  const output = await workbook.xlsx.writeBuffer();
  return new Blob([output as BlobPart], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' });
};

export const mergePdfs = async (files: File[]): Promise<Blob> => {
  if (files.length < 2) throw new Error('Select at least two PDFs to merge.');
  if (files.length > 20) throw new Error('Merge at most 20 PDFs at once.');
  if (files.reduce((sum, file) => sum + file.size, 0) > 150 * 1024 * 1024) throw new Error('The selected PDFs exceed the 150 MB combined limit.');
  const output = await PDFDocument.create();
  for (const file of files) {
    const source = await PDFDocument.load(await file.arrayBuffer(), { ignoreEncryption: false });
    const pages = await output.copyPages(source, source.getPageIndices());
    pages.forEach((page) => output.addPage(page));
  }
  return new Blob([await output.save({ useObjectStreams: true }) as BlobPart], { type: 'application/pdf' });
};

export const editPdfPages = async (
  file: File,
  selectedPages: number[],
  rotations: Record<number, number>
): Promise<Blob> => {
  const source = await PDFDocument.load(await file.arrayBuffer(), { ignoreEncryption: false });
  if (selectedPages.length === 0) throw new Error('Keep at least one page.');
  const output = await PDFDocument.create();
  const copied = await output.copyPages(source, selectedPages.map((page) => page - 1));
  copied.forEach((page, index) => {
    const pageNumber = selectedPages[index];
    page.setRotation(degrees((page.getRotation().angle + (rotations[pageNumber] ?? 0)) % 360));
    output.addPage(page);
  });
  return new Blob([await output.save({ useObjectStreams: true }) as BlobPart], { type: 'application/pdf' });
};

export const splitPdfToZip = async (file: File): Promise<Blob> => {
  const source = await PDFDocument.load(await file.arrayBuffer(), { ignoreEncryption: false });
  if (source.getPageCount() > 500) throw new Error('This PDF exceeds the 500-page split limit.');
  const zip = new JSZip();
  const digits = String(source.getPageCount()).length;
  for (let index = 0; index < source.getPageCount(); index += 1) {
    const pageDocument = await PDFDocument.create();
    const [page] = await pageDocument.copyPages(source, [index]);
    pageDocument.addPage(page);
    zip.file(`page-${String(index + 1).padStart(digits, '0')}.pdf`, await pageDocument.save({ useObjectStreams: true }));
  }
  return zip.generateAsync({ type: 'blob', compression: 'DEFLATE', compressionOptions: { level: 6 }, mimeType: 'application/zip' });
};

export const getPdfPageCount = async (file: File): Promise<number> => {
  const pdf = await PDFDocument.load(await file.arrayBuffer(), { ignoreEncryption: false });
  return pdf.getPageCount();
};
