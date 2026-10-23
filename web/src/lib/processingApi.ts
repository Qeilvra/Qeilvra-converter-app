import type { ExtractedTable } from '../types';

const rawBaseUrl = (import.meta.env.VITE_PROCESSING_API_URL as string | undefined)?.trim() || '/api/v1';
const baseUrl = rawBaseUrl.replace(/\/+$/, '');
// Merely loading a conversion screen must never opt a local document into uploading.
export const configuredOfficeService = (import.meta.env.VITE_PROCESSING_API_URL as string | undefined)?.trim().replace(/\/+$/, '') || null;
const REQUEST_TIMEOUT_MS = 150_000;
const MAX_OUTPUT_BYTES = 100 * 1024 * 1024;
const MAX_JSON_BYTES = 8 * 1024 * 1024;

const readBoundedBlob = async (response: Response, limit: number): Promise<Blob> => {
  if (Number(response.headers?.get('Content-Length') ?? 0) > limit) throw new Error('The service output exceeds the browser download limit. Try a smaller document.');
  if (!response.body) {
    const blob = await response.blob();
    if (blob.size > limit) throw new Error('The service output exceeds the browser download limit. Try a smaller document.');
    return blob;
  }
  const reader = response.body.getReader(); const chunks: BlobPart[] = []; let total = 0;
  try {
    while (true) {
      const { done, value } = await reader.read(); if (done) break;
      total += value.byteLength;
      if (total > limit) { await reader.cancel(); throw new Error('The service output exceeds the browser download limit. Try a smaller document.'); }
      chunks.push(value as BlobPart);
    }
    return new Blob(chunks, { type: response.headers.get('Content-Type') ?? '' });
  } finally { reader.releaseLock(); }
};

const readJson = async <T>(response: Response, limit = MAX_JSON_BYTES): Promise<T> => {
  try { return JSON.parse(await (await readBoundedBlob(response, limit)).text()) as T; }
  catch (reason) {
    if (reason instanceof SyntaxError) throw new Error('The service returned invalid JSON. Check the backend endpoint and retry.');
    throw reason;
  }
};

type ZipByteStream = {
  on(event: 'data', callback: (chunk: Uint8Array) => void): ZipByteStream;
  on(event: 'end', callback: () => void): ZipByteStream;
  on(event: 'error', callback: (error: Error) => void): ZipByteStream;
  pause(): void;
  resume(): void;
};
const validateXmlEntry = (entry: import('jszip').JSZipObject, expectedRoot: string, namespaces: string[]): Promise<void> => new Promise((resolve, reject) => {
  let bytes = 0;
  const decoder = new TextDecoder(); const parts: string[] = [];
  // JSZip exposes this streaming API at runtime, but omits it from JSZipObject's declarations.
  const stream = (entry as typeof entry & { internalStream(type: 'uint8array'): ZipByteStream }).internalStream('uint8array');
  stream.on('data', (chunk: Uint8Array) => {
    bytes += chunk.byteLength;
    if (bytes > 20 * 1024 * 1024) { stream.pause(); reject(new Error('Document XML exceeds the browser validation limit.')); return; }
    parts.push(decoder.decode(chunk, { stream: true }));
  });
  stream.on('error', reject);
  stream.on('end', () => {
    try {
      const xml = parts.join('') + decoder.decode();
      if (/<!DOCTYPE|<!ENTITY/i.test(xml)) throw new Error('Unsupported document XML declarations.');
      const document = new DOMParser().parseFromString(xml, 'application/xml');
      const root = document.documentElement;
      if (document.getElementsByTagName('parsererror').length || root.localName !== expectedRoot || !namespaces.includes(root.namespaceURI ?? '')) throw new Error('Invalid document XML.');
      resolve();
    } catch (reason) { reject(reason); }
  });
  stream.resume();
});

const requireSecureService = () => {
  if (import.meta.env.PROD && /^http:\/\//i.test(baseUrl)) throw new Error('The production processing service must use HTTPS.');
};

const messageFromResponse = async (response: Response) => {
  try {
    const body = await readJson<{ error?: string }>(response, 256 * 1024);
    if (typeof body.error === 'string') return body.error;
  } catch { /* Reverse proxies may send an HTML error page. */ }
  if (response.status === 404) return 'The processing endpoint was not found. Configure the hosted FileFlow backend URL ending in /api/v1, or its reverse proxy. A web-only deployment cannot convert documents.';
  if (response.status === 413) return 'The file exceeds the processing service limit.';
  if (response.status === 429) return 'The service is busy with too many requests. Wait a moment and retry.';
  if (response.status === 503) return 'The required conversion engine is unavailable. Try again when the processing service is ready.';
  return `The processing service returned ${response.status}. Please retry or check the service configuration.`;
};

// Keep the timeout active until the response body is consumed, including stalled downloads.
const request = async <T>(path: string, init: RequestInit, read: (response: Response) => Promise<T>, timeoutMs = REQUEST_TIMEOUT_MS): Promise<T> => {
  requireSecureService();
  const controller = new AbortController();
  const timer = window.setTimeout(() => controller.abort(), timeoutMs);
  try {
    const response = await fetch(`${baseUrl}${path}`, { ...init, signal: controller.signal, credentials: 'omit', cache: 'no-store' });
    if (!response.ok) throw new Error(await messageFromResponse(response));
    return await read(response);
  } catch (reason) {
    if (controller.signal.aborted) throw new Error('Processing timed out. Try a smaller file and retry.');
    if (reason instanceof TypeError) throw new Error('The processing service could not be reached. Check your connection and the backend URL, HTTPS and CORS configuration, then retry.');
    throw reason;
  } finally { window.clearTimeout(timer); }
};

type OutputKind = 'pdf' | 'docx' | 'xlsx';
const readDocument = async (response: Response, kind: OutputKind): Promise<Blob> => {
  const blob = await readBoundedBlob(response, MAX_OUTPUT_BYTES);
  if (!blob.size) throw new Error('The service returned an empty file. No output was saved.');
  if (kind === 'pdf') {
    const { PDFDocument } = await import('pdf-lib');
    try {
      const document = await PDFDocument.load(await blob.arrayBuffer());
      if (!document.getPageCount()) throw new Error('No pages');
    } catch { throw new Error('The service did not return a valid PDF. Check the backend endpoint and retry.'); }
  } else {
    const { default: JSZip } = await import('jszip');
    try {
      const zip = await JSZip.loadAsync(await blob.arrayBuffer());
      const required: Array<[string, string, string[]]> = [
        ['[Content_Types].xml', 'Types', ['http://schemas.openxmlformats.org/package/2006/content-types']],
        kind === 'docx'
          ? ['word/document.xml', 'document', ['http://schemas.openxmlformats.org/wordprocessingml/2006/main', 'http://purl.oclc.org/ooxml/wordprocessingml/main']]
          : ['xl/workbook.xml', 'workbook', ['http://schemas.openxmlformats.org/spreadsheetml/2006/main', 'http://purl.oclc.org/ooxml/spreadsheetml/main']]
      ];
      for (const [path, root, namespaces] of required) {
        const entry = zip.file(path);
        if (!entry) throw new Error('Missing document data');
        await validateXmlEntry(entry, root, namespaces);
      }
    } catch { throw new Error(`The service did not return a valid ${kind.toUpperCase()} document. Check the backend endpoint and retry.`); }
  }
  const mime = kind === 'pdf' ? 'application/pdf' : kind === 'docx' ? 'application/vnd.openxmlformats-officedocument.wordprocessingml.document' : 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet';
  return new Blob([blob], { type: mime });
};

const formFor = (file: File) => { const form = new FormData(); form.append('file', file, file.name); return form; };

export const convertDocument = async (file: File, direction: 'pdf-word' | 'word-pdf') => request(
  direction === 'pdf-word' ? '/convert/pdf-to-docx' : '/convert/docx-to-pdf',
  { method: 'POST', body: formFor(file) }, (response) => readDocument(response, direction === 'pdf-word' ? 'docx' : 'pdf')
);

export type CompressionLevel = 'LOW' | 'BALANCED' | 'HIGH';
export type PdfCompressionResult = { blob: Blob; reduced: boolean; imagesOptimized: number };
export const compressPdfRemote = async (file: File, level: CompressionLevel): Promise<PdfCompressionResult> => request(
  `/pdf/compress?level=${level}`, { method: 'POST', body: formFor(file) }, async (response) => {
    const blob = await readDocument(response, 'pdf');
    return { blob, reduced: blob.size < file.size, imagesOptimized: Number(response.headers.get('X-FileFlow-Images-Optimized') ?? 0) || 0 };
  }
);

type TableResponse = { tables: ExtractedTable[]; pageCount: number; scanned: boolean };
export const extractPdfTablesRemote = async (file: File): Promise<TableResponse> => request(
  '/pdf/tables', { method: 'POST', body: formFor(file) }, async (response) => {
    const data = await readJson<TableResponse>(response);
    if (!data || !Array.isArray(data.tables) || !Number.isInteger(data.pageCount) || typeof data.scanned !== 'boolean' || data.tables.some((table) =>
      !table || typeof table.id !== 'string' || !Number.isInteger(table.page) || !Array.isArray(table.rows) || table.rows.some((row) => !Array.isArray(row) || row.some((cell) => typeof cell?.value !== 'string'))
    )) throw new Error('The service returned invalid table data. Check the backend configuration and retry.');
    return data;
  }
);

export const tablesToXlsxRemote = async (tables: ExtractedTable[]): Promise<Blob> => request('/pdf/tables/xlsx', {
  method: 'POST', headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ tables, pageCount: 0, scanned: false })
}, (response) => readDocument(response, 'xlsx'));

export type ProcessingOperation = 'pdf-to-docx' | 'docx-to-pdf' | 'pdf-tables' | 'pdf-compress';
export type ServiceHealth = { status: 'ready' | 'degraded'; operations: ProcessingOperation[] };
export const checkProcessingService = async (): Promise<ServiceHealth> => request('/health/capabilities', {}, async (response) => {
  const data = await readJson<ServiceHealth>(response, 128 * 1024);
  if (!data || !['ready', 'degraded'].includes(data.status) || !Array.isArray(data.operations)) throw new Error('The configured URL does not expose FileFlow service health. Check the backend URL ending in /api/v1.');
  return data;
}, 60_000);

export const processingApiDescription = 'This tool uploads a temporary copy to the configured FileFlow service. The worker attempts cleanup after each request; processing availability is checked below.';
