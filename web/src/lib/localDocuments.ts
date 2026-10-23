import type { ExtractedTable } from '../types';
import type { PDFPageProxy } from 'pdfjs-dist';

export type Progress = (message: string) => void;
export type Span = import('./pdfWordLayout').WordSpan;
export type Line = { y: number; size: number; spans: Span[]; cells: Span[][] };
export type PageText = import('./pdfWordLayout').WordPage;
const MAX_FILE = 20 * 1024 * 1024;
const MAX_PAGES = 100;
const MAX_CHARS = 2_000_000;
const pause = () => new Promise<void>((resolve) => setTimeout(resolve, 0));

// PDF.js and its worker are emitted into this site's own assets; no document or CDN request.
async function openPdf(file: File) {
  if (!file.size || file.size > MAX_FILE) throw new Error('Local conversion supports files up to 20 MB. Split larger documents first.');
  const pdfjs = await import('pdfjs-dist');
  pdfjs.GlobalWorkerOptions.workerSrc = (await import('pdfjs-dist/build/pdf.worker.min.mjs?url')).default;
  const task = pdfjs.getDocument({ data: new Uint8Array(await file.arrayBuffer()), useSystemFonts: true, stopAtErrors: true, fontExtraProperties: true, maxImageSize: 16_000_000 });
  const deadline = setTimeout(() => { void task.destroy(); }, 120_000);
  try {
    const pdf = await task.promise;
    if (pdf.numPages > MAX_PAGES) throw new Error('Local conversion supports at most 100 pages. Split this PDF first.');
    const fields = await pdf.getFieldObjects();
    if (fields?.size) throw new Error('PDF form fields are not supported by local editable conversion. Export a static copy from your PDF reader first.');
    return { pdf, close: () => { clearTimeout(deadline); return task.destroy(); } };
  } catch (error) { clearTimeout(deadline); await task.destroy(); throw error; }
}

export function groupLines(spans: Span[]): Line[] {
  const lines: Line[] = [];
  for (const span of [...spans].sort((a, b) => a.y - b.y || a.x - b.x)) {
    let line = lines.at(-1);
    if (!line || Math.abs(line.y - span.y) > Math.max(2, Math.min(line.size, span.size) * .3)) {
      line = { y: span.y, size: span.size, spans: [], cells: [] }; lines.push(line);
    }
    line.spans.push(span); line.size = Math.max(line.size, span.size);
  }
  for (const line of lines) {
    line.spans.sort((a, b) => a.x - b.x);
    for (const span of line.spans) {
      const cell = line.cells.at(-1); const previous = cell?.at(-1);
      if (!previous || span.x - previous.x - previous.width > Math.max(12, line.size * 1.2)) line.cells.push([span]);
      else cell!.push(span);
    }
  }
  return lines;
}

export function cellText(spans: Span[]): string {
  return spans.map((span, index) => {
    const previous = spans[index - 1];
    const gap = previous ? span.x - previous.x - previous.width : 0;
    return `${previous && gap > span.size * .15 && !/\s$/.test(previous.text) && !/^\s/.test(span.text) ? ' ' : ''}${span.text}`;
  }).join('');
}

async function pageText(page: PDFPageProxy, withFonts = false): Promise<PageText> {
  const { Util } = await import('pdfjs-dist');
  const viewport = page.getViewport({ scale: 1 });
  if (withFonts) await page.getOperatorList();
  const content = await page.getTextContent();
  const spans: Span[] = [];
  let chars = 0;
  for (const item of content.items) {
    if (!('str' in item) || !item.str.trim()) continue;
    if ((chars += item.str.length) > MAX_CHARS) throw new Error('This page exceeds the local text limit.');
    const matrix = Util.transform(viewport.transform, item.transform);
    if (Math.abs(matrix[1]) > .2 || Math.abs(matrix[2]) > .2 || item.dir === 'ttb') throw new Error('Rotated or vertical text is not supported by local editable conversion.');
    const style = content.styles[item.fontName];
    const font = page.commonObjs.has(item.fontName) ? page.commonObjs.get(item.fontName) as { name?: string } : undefined;
    spans.push({ text: item.str, x: matrix[4], y: matrix[5], width: item.width, size: Math.hypot(matrix[2], matrix[3]), font: font?.name ?? style?.fontFamily ?? 'Arial', ascent: style?.ascent });
  }
  const paint = withFonts ? await (await import('./pdfWordPaint')).readWordPaint(page, spans) : undefined;
  const warnings: string[] = [];
  if (paint?.unmatched) warnings.push(`${paint.unmatched} text spans could not be matched reliably to paint operators; their text color may use a default.`);
  if (paint?.unsupportedPaths) warnings.push(`${paint.unsupportedPaths} complex vector paths were not reconstructed. Simple rules, text underlines and detected table borders are handled separately.`);
  return { width: viewport.width, height: viewport.height, lines: groupLines(spans), rules: paint?.rules, warnings };
}

// A table must contain two adjacent multi-cell lines. Blank/misaligned cells remain reviewable.
export function tableBlocks(lines: Line[]): Array<{ start: number; end: number; rows: Span[][][] }> {
  const blocks: Array<{ start: number; end: number; rows: Span[][][] }> = [];
  for (let start = 0; start < lines.length;) {
    if (lines[start].cells.length < 2) { start++; continue; }
    let end = start + 1;
    while (end < lines.length && lines[end].cells.length >= 2 && lines[end].y - lines[end - 1].y <= Math.max(45, lines[end].size * 3)) end++;
    if (end - start >= 2) {
      const template = lines.slice(start, end).reduce((a, b) => a.cells.length >= b.cells.length ? a : b);
      const anchors = template.cells.map((cell) => cell[0].x);
      const rows = lines.slice(start, end).map((line) => {
        const cells: Span[][] = anchors.map(() => []);
        for (const cell of line.cells) {
          const nearest = anchors.reduce((best, x, index) => Math.abs(x - cell[0].x) < Math.abs(anchors[best] - cell[0].x) ? index : best, 0);
          cells[nearest].push(...cell);
        }
        return cells;
      });
      blocks.push({ start, end, rows });
    }
    start = end;
  }
  return blocks;
}

export async function extractPdfTablesLocal(file: File, progress?: Progress) {
  const { pdf, close } = await openPdf(file); const tables: ExtractedTable[] = []; const emptyPages: number[] = [];
  let chars = 0; let cells = 0;
  try {
    for (let number = 1; number <= pdf.numPages; number++) {
      progress?.(`Reading page ${number} of ${pdf.numPages}`); await pause();
      const page = await pdf.getPage(number);
      try {
        const model = await pageText(page);
        if (!model.lines.length) emptyPages.push(number);
        for (const block of tableBlocks(model.lines)) {
          const rows = block.rows.map((row) => row.map((cell) => ({ value: cellText(cell), uncertain: true })));
          for (const row of rows) for (const cell of row) { chars += cell.value.length; cells++; }
          if (chars > MAX_CHARS || cells > 10_000 || tables.length >= 100) throw new Error('Extracted tables exceed the local review limit (10,000 cells). Split this PDF first.');
          tables.push({ id: `p${number}-t${tables.length + 1}`, page: number, rows });
        }
      } finally { page.cleanup(); }
    }
    return { tables, pageCount: pdf.numPages, scanned: emptyPages.length === pdf.numPages, emptyPages };
  } finally { await close(); }
}

function typedValue(value: string, type: 'text' | 'number' | 'date'): string | number | Date {
  if (!value || type === 'text') return value;
  if (type === 'number') {
    // No trimming, locale inference, currency removal, leading-zero loss, or rounding.
    if (!/^-?(?:0|[1-9]\d*)(?:\.\d+)?$/.test(value) || value.replace(/[-.]/g, '').length > 15 || !Number.isFinite(Number(value))) throw new Error(`“${value}” cannot be safely converted to a number. Keep this column as Text.`);
    return Number(value);
  }
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value) || value < '1900-01-01' || value > '9999-12-31') throw new Error(`“${value}” is not a supported YYYY-MM-DD date. Keep this column as Text.`);
  const date = new Date(`${value}T00:00:00Z`);
  if (!Number.isFinite(date.getTime()) || date.toISOString().slice(0, 10) !== value) throw new Error(`“${value}” is not a valid date.`);
  return date;
}

export async function tablesToXlsxLocal(tables: ExtractedTable[]): Promise<Blob> {
  if (!tables.length || tables.length > 100) throw new Error('Export between 1 and 100 tables.');
  const { default: ExcelJS } = await import('exceljs');
  const workbook = new ExcelJS.Workbook(); let count = 0; let chars = 0;
  for (const [index, table] of tables.entries()) {
    const sheet = workbook.addWorksheet(`Table ${index + 1} p${table.page}`);
    if (!table.rows.length || table.rows.length > 100_000) throw new Error('The table exceeds the row limit.');
    table.rows.forEach((row, rowIndex) => {
      if (row.length > 16384 || (count += row.length) > 100_000) throw new Error('The workbook exceeds the local cell limit.');
      sheet.addRow(row.map((cell, column) => {
        if (cell.value.length > 32767 || (chars += cell.value.length) > MAX_CHARS || /[\x00-\x08\x0b\x0c\x0e-\x1f\ufffe\uffff]/.test(cell.value)) throw new Error('A cell exceeds Excel limits or contains invalid control characters. Correct it before export.');
        return typedValue(cell.value, rowIndex ? table.columnTypes?.[column] ?? 'text' : 'text');
      }));
    });
    sheet.views = [{ state: 'frozen', ySplit: 1 }];
    table.columnTypes?.forEach((type, column) => { if (type === 'date') sheet.getColumn(column + 1).numFmt = 'yyyy-mm-dd'; });
  }
  return new Blob([await workbook.xlsx.writeBuffer() as BlobPart], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' });
}

async function pageImages(page: PDFPageProxy): Promise<import('./pdfWordExport').WordImage[]> {
  const { OPS, Util } = await import('pdfjs-dist');
  const operators = await page.getOperatorList(); const images: import('./pdfWordExport').WordImage[] = [];
  let matrix = [1, 0, 0, 1, 0, 0]; const stack: number[][] = []; let pixels = 0;
  for (let index = 0; index < operators.fnArray.length; index++) {
    const op = operators.fnArray[index]; const args = operators.argsArray[index];
    if (op === OPS.save) stack.push([...matrix]);
    else if (op === OPS.restore) matrix = stack.pop() ?? matrix;
    else if (op === OPS.transform) matrix = Util.transform(matrix, args);
    else if (op === OPS.paintImageXObject || op === OPS.paintInlineImageXObject) {
      const img = op === OPS.paintInlineImageXObject ? args[0] : await new Promise<any>((resolve) => {
        const objects = String(args[0]).startsWith('g_') ? page.commonObjs : page.objs;
        objects.get(args[0], resolve);
      });
      if (!img?.width || !img?.height || (pixels += img.width * img.height) > 16_000_000 || images.length >= 50) throw new Error('Embedded images exceed the safe local conversion limit.');
      const canvas = document.createElement('canvas'); canvas.width = img.width; canvas.height = img.height;
      try {
        const context = canvas.getContext('2d')!;
        if (img.bitmap) context.drawImage(img.bitmap, 0, 0);
        else {
          const rgba = new Uint8ClampedArray(img.width * img.height * 4);
          if (img.data.length === rgba.length) rgba.set(img.data);
          else if (img.data.length === img.width * img.height * 3) {
            for (let p = 0, c = 0; p < rgba.length; p += 4, c += 3) { rgba[p] = img.data[c]; rgba[p + 1] = img.data[c + 1]; rgba[p + 2] = img.data[c + 2]; rgba[p + 3] = 255; }
          } else throw new Error('An embedded image uses an unsupported pixel format.');
          context.putImageData(new ImageData(rgba, img.width, img.height), 0, 0);
        }
        const blob = await new Promise<Blob>((resolve, reject) => canvas.toBlob((value) => value ? resolve(value) : reject(new Error('Image export failed.')), 'image/png'));
        const viewport = page.getViewport({ scale: 1 });
        const corners = [[0, 0], [0, 1], [1, 0], [1, 1]].map(p => { Util.applyTransform(p, matrix); Util.applyTransform(p, viewport.transform); return p; });
        const x = Math.min(...corners.map(p => p[0])); const y = Math.min(...corners.map(p => p[1]));
        if (Math.abs(matrix[1]) > .2 || Math.abs(matrix[2]) > .2 || matrix[0] < 0 || matrix[3] < 0) throw new Error('Rotated or mirrored embedded images are not supported by local editable conversion.');
        images.push({ data: new Uint8Array(await blob.arrayBuffer()), width: Math.max(...corners.map(p => p[0])) - x, height: Math.max(...corners.map(p => p[1])) - y, x, y });
      } finally { canvas.width = 0; canvas.height = 0; }
    }
  }
  return images;
}

export async function pdfToDocxLocal(file: File, progress?: Progress): Promise<Blob> {
  // Inspect compressed image dimensions before PDF.js decodes them. Tiny PDFs can hide huge bitmaps.
  if (!file.size || file.size > MAX_FILE) throw new Error('Local conversion supports files up to 20 MB.');
  const { PDFDocument, PDFRawStream, PDFName, PDFNumber } = await import('pdf-lib');
  const source = await PDFDocument.load(await file.arrayBuffer(), { ignoreEncryption: false });
  let pixels = 0;
  for (const [, object] of source.context.enumerateIndirectObjects()) {
    if (!(object instanceof PDFRawStream) || object.dict.get(PDFName.of('Subtype'))?.toString() !== '/Image') continue;
    const width = object.dict.lookup(PDFName.of('Width'), PDFNumber).asNumber();
    const height = object.dict.lookup(PDFName.of('Height'), PDFNumber).asNumber();
    if (!Number.isFinite(width * height) || width <= 0 || height <= 0 || width * height > 16_000_000 || (pixels += width * height) > 32_000_000) throw new Error('Embedded images exceed the safe local conversion limit.');
  }
  const { pdf, close } = await openPdf(file);
  const pages: import('./pdfWordExport').WordSourcePage[] = [];
  let chars = 0; let imageBytes = 0;
  try {
    for (let number = 1; number <= pdf.numPages; number++) {
      progress?.(`Reconstructing page ${number} of ${pdf.numPages}`); await pause();
      const page = await pdf.getPage(number);
      try {
        const model = await pageText(page, true);
        chars += model.lines.reduce((sum, line) => sum + cellText(line.spans).length, 0);
        if (chars > MAX_CHARS) throw new Error('This PDF exceeds the local text limit.');
        if (!model.lines.length) throw new Error(`Page ${number} has no selectable text. Offline OCR is required; no image-only Word file was created.`);
        const images = await pageImages(page);
        imageBytes += images.reduce((sum, image) => sum + image.data.byteLength, 0);
        if (imageBytes > 30 * 1024 * 1024) throw new Error('Extracted images exceed the local document limit.');
        pages.push({ ...model, images });
      } finally { page.cleanup(); }
    }
    progress?.('Creating editable Word file');
    return await (await import('./pdfWordExport')).exportEditableWord(pages);
  } finally { await close(); }
}

export async function compressPdfLocal(file: File) {
  if (file.size > MAX_FILE) throw new Error('Local PDF optimization supports up to 20 MB.');
  const { PDFDocument, PDFSignature } = await import('pdf-lib');
  const pdf = await PDFDocument.load(await file.arrayBuffer(), { ignoreEncryption: false });
  if (pdf.getPageCount() > 500) throw new Error('Optimize at most 500 pages.');
  if (pdf.getForm().getFields().some((field) => field instanceof PDFSignature)) throw new Error('Signed PDFs must be kept unchanged.');
  const bytes = await pdf.save({ useObjectStreams: true });
  const blob = new Blob([bytes as BlobPart], { type: 'application/pdf' });
  return { blob: blob.size < file.size ? blob : file, reduced: blob.size < file.size, imagesOptimized: 0 };
}
