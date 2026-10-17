import type { ExtractedTable } from '../types';

const rawBaseUrl = (import.meta.env.VITE_PROCESSING_API_URL as string | undefined) ?? '/api/v1';
const baseUrl = rawBaseUrl.replace(/\/$/, '');

const requireSecureService = () => {
  if (import.meta.env.PROD && /^http:\/\//i.test(baseUrl)) throw new Error('The production processing service must use HTTPS.');
};

type ErrorBody = { error?: string };

const messageFromResponse = async (response: Response) => {
  try {
    const body = await response.json() as ErrorBody;
    if (body.error) return body.error;
  } catch { /* A reverse proxy may return a non-JSON error page. */ }
  return response.status === 413 ? 'The file exceeds the processing service limit.' : `The processing service returned ${response.status}.`;
};

const upload = async (path: string, file: File, query = ''): Promise<{ blob: Blob; headers: Headers }> => {
  requireSecureService();
  const form = new FormData();
  form.append('file', file, file.name);
  let response: Response;
  try {
    response = await fetch(`${baseUrl}${path}${query}`, { method: 'POST', body: form, credentials: 'omit', cache: 'no-store' });
  } catch {
    throw new Error('The secure processing service is unavailable. Check the service URL and try again.');
  }
  if (!response.ok) throw new Error(await messageFromResponse(response));
  return { blob: await response.blob(), headers: response.headers };
};

export const convertDocument = async (file: File, direction: 'pdf-word' | 'word-pdf') =>
  (await upload(direction === 'pdf-word' ? '/convert/pdf-to-docx' : '/convert/docx-to-pdf', file)).blob;

export type CompressionLevel = 'LOW' | 'BALANCED' | 'HIGH';
export type PdfCompressionResult = { blob: Blob; reduced: boolean; imagesOptimized: number };

export const compressPdfRemote = async (file: File, level: CompressionLevel): Promise<PdfCompressionResult> => {
  const result = await upload('/pdf/compress', file, `?level=${level}`);
  return {
    blob: result.blob,
    reduced: result.headers.get('X-FileFlow-Reduced') === 'true',
    imagesOptimized: Number(result.headers.get('X-FileFlow-Images-Optimized') ?? 0)
  };
};

type TableResponse = { tables: ExtractedTable[]; pageCount: number; scanned: boolean };

export const extractPdfTablesRemote = async (file: File): Promise<TableResponse> => {
  requireSecureService();
  const form = new FormData();
  form.append('file', file, file.name);
  let response: Response;
  try {
    response = await fetch(`${baseUrl}/pdf/tables`, { method: 'POST', body: form, credentials: 'omit', cache: 'no-store' });
  } catch {
    throw new Error('The secure table-recognition service is unavailable.');
  }
  if (!response.ok) throw new Error(await messageFromResponse(response));
  return await response.json() as TableResponse;
};

export const tablesToXlsxRemote = async (tables: ExtractedTable[]): Promise<Blob> => {
  requireSecureService();
  const response = await fetch(`${baseUrl}/pdf/tables/xlsx`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ tables, pageCount: 0, scanned: false }), credentials: 'omit', cache: 'no-store'
  });
  if (!response.ok) throw new Error(await messageFromResponse(response));
  return response.blob();
};

export const processingApiDescription = 'Sent to the configured FileFlow service. Temporary files are deleted after each request; production service URLs must use HTTPS.';
