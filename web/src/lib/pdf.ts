import JSZip from 'jszip';
import { degrees, PDFDocument } from 'pdf-lib';

export const mergePdfs = async (files: File[]): Promise<Blob> => {
  if (files.length < 2) throw new Error('Select at least two PDFs to merge.');
  if (files.length > 20) throw new Error('Merge at most 20 PDFs at once.');
  if (files.reduce((sum, file) => sum + file.size, 0) > 150 * 1024 * 1024) throw new Error('The selected PDFs exceed the 150 MB combined limit.');
  const output = await PDFDocument.create();
  let pageCount = 0;
  for (const file of files) {
    const source = await PDFDocument.load(await file.arrayBuffer(), { ignoreEncryption: false });
    pageCount += source.getPageCount();
    if (pageCount > 500) throw new Error('Merge at most 500 pages in one browser session.');
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
  if (source.getPageCount() > 500) throw new Error('This PDF exceeds the 500-page browser editing limit.');
  if (selectedPages.length === 0) throw new Error('Keep at least one page.');
  if (selectedPages.some((page) => !Number.isInteger(page) || page < 1 || page > source.getPageCount())) throw new Error('The selected page numbers are invalid.');
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
  if (pdf.getPageCount() > 500) throw new Error('This PDF exceeds the 500-page browser editing limit.');
  return pdf.getPageCount();
};
