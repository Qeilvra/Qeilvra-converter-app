import JSZip from 'jszip';

// Deliberately narrow: a text reflow export, with unsupported content rejected before saving.
export async function docxToPdfLocal(file: File): Promise<Blob> {
  if (!file.size || file.size > 20 * 1024 * 1024) throw new Error('Local Word export supports files up to 20 MB.');
  const zip = await JSZip.loadAsync(await file.arrayBuffer());
  const entry = zip.file('word/document.xml');
  if (!entry) throw new Error('This DOCX has no main document.');
  // Bound expansion using the same streaming validation as remote results.
  const xml = await new Promise<string>((resolve, reject) => {
    let size = 0; const parts: Uint8Array[] = [];
    type Stream = { on(event: 'data', callback: (chunk: Uint8Array) => void): void; on(event: 'error', callback: (error: Error) => void): void; on(event: 'end', callback: () => void): void; pause(): void; resume(): void };
    const stream = (entry as typeof entry & { internalStream(type: 'uint8array'): Stream }).internalStream('uint8array');
    stream.on('data', (chunk: Uint8Array) => { size += chunk.length; if (size > 8 * 1024 * 1024) { stream.pause(); reject(new Error('Word document XML exceeds the local limit.')); } else parts.push(chunk); });
    stream.on('error', reject); stream.on('end', () => { void new Blob(parts as BlobPart[]).text().then(resolve, reject); }); stream.resume();
  });
  if (/<!DOCTYPE|<!ENTITY/i.test(xml)) throw new Error('Unsupported document XML declarations.');
  const document = new DOMParser().parseFromString(xml, 'application/xml');
  if (document.querySelector('parsererror')) throw new Error('This DOCX contains invalid XML.');
  const unsupported = ['drawing', 'pict', 'object', 'tbl', 'altChunk', 'sym', 'fldChar', 'txbxContent', 'footnoteReference', 'endnoteReference', 'headerReference', 'footerReference', 'numPr', 'oMath', 'del', 'ins'];
  if (unsupported.some((name) => document.getElementsByTagNameNS('*', name).length)) throw new Error('Local Word-to-PDF supports simple text documents only. Tables, images, fields, notes and headers need a full document renderer. Use Word or LibreOffice on your device to export this file.');
  const paragraphs = [...document.getElementsByTagNameNS('*', 'p')].map((paragraph) => {
    const walk = (node: Node): string => (node as Element).localName === 't' ? node.textContent ?? '' : (node as Element).localName === 'tab' ? '    ' : (node as Element).localName === 'br' ? '\n' : [...node.childNodes].map(walk).join('');
    return walk(paragraph);
  });
  if (!paragraphs.join('').trim()) throw new Error('No readable text was found.');
  if (paragraphs.join('').length > 1_000_000) throw new Error('This document exceeds the local text limit.');
  if (/[^\x09\x0a\x0d\x20-\x7e]/.test(paragraphs.join(''))) throw new Error('This local PDF text exporter supports basic Latin text only. Use Word or LibreOffice for this document’s fonts and script.');
  const { jsPDF } = await import('jspdf'); const pdf = new jsPDF({ unit: 'pt', format: 'a4' });
  pdf.setFont('helvetica'); pdf.setFontSize(11); let y = 54; let pages = 1;
  for (const paragraph of paragraphs) {
    for (const line of pdf.splitTextToSize(paragraph || ' ', 487) as string[]) {
      if (y > 780) { if (++pages > 100) throw new Error('This Word document exceeds the 100-page local limit.'); pdf.addPage(); y = 54; }
      pdf.text(line, 54, y); y += 14;
    }
    y += 6;
  }
  return pdf.output('blob');
}
