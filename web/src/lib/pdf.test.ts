// @vitest-environment jsdom
import JSZip from 'jszip';
import { PDFDocument } from 'pdf-lib';
import { describe, expect, it } from 'vitest';
import { editPdfPages, mergePdfs, splitPdfToZip } from './pdf';

const samplePdf = async (pages: number, name: string): Promise<File> => {
  const document = await PDFDocument.create();
  for (let index = 0; index < pages; index += 1) document.addPage([300, 400]);
  return new File([await document.save() as BlobPart], name, { type: 'application/pdf' });
};

describe('PDF processing', () => {
  it('merges every page into a parseable PDF', async () => {
    const output = await mergePdfs([await samplePdf(2, 'one.pdf'), await samplePdf(3, 'two.pdf')]);
    const parsed = await PDFDocument.load(await output.arrayBuffer());
    expect(parsed.getPageCount()).toBe(5);
  });

  it('reorders, removes and rotates pages into a parseable PDF', async () => {
    const output = await editPdfPages(await samplePdf(3, 'source.pdf'), [3, 1], { 3: 90, 1: 180 });
    const parsed = await PDFDocument.load(await output.arrayBuffer());
    expect(parsed.getPageCount()).toBe(2);
    expect(parsed.getPage(0).getRotation().angle).toBe(90);
    expect(parsed.getPage(1).getRotation().angle).toBe(180);
  });

  it('splits every page into individually parseable PDFs in a ZIP', async () => {
    const output = await splitPdfToZip(await samplePdf(3, 'source.pdf'));
    const zip = await JSZip.loadAsync(await output.arrayBuffer());
    expect(Object.keys(zip.files)).toEqual(['page-1.pdf', 'page-2.pdf', 'page-3.pdf']);
    const second = zip.file('page-2.pdf');
    expect(second).not.toBeNull();
    const parsed = await PDFDocument.load(await second!.async('arraybuffer'));
    expect(parsed.getPageCount()).toBe(1);
  });
  it('rejects invalid page selections and excessive editing workloads', async () => {
    await expect(editPdfPages(await samplePdf(1, 'one.pdf'), [0], {})).rejects.toThrow(/invalid/);
    await expect(editPdfPages(await samplePdf(501, 'large.pdf'), [1], {})).rejects.toThrow(/500-page/);
  });

});
