// @vitest-environment jsdom
import ExcelJS from 'exceljs';
import JSZip from 'jszip';
import { PDFDocument } from 'pdf-lib';
import { describe, expect, it } from 'vitest';
import { editPdfPages, mergePdfs, splitPdfToZip, tablesToXlsx } from './pdf';

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

});

describe('Excel output', () => {
  it('creates a valid workbook with editable cells', async () => {
    const blob = await tablesToXlsx([{ id: 'table-1', page: 1, rows: [[{ value: 'Name', uncertain: false }, { value: 'Score', uncertain: false }], [{ value: 'Ayesha', uncertain: false }, { value: '92', uncertain: true }]] }]);
    const workbook = new ExcelJS.Workbook();
    await workbook.xlsx.load(await blob.arrayBuffer());
    expect(workbook.worksheets.map((sheet) => sheet.name)).toEqual(['Table 1 P1']);
    expect(workbook.getWorksheet('Table 1 P1')?.getCell('A2').value).toBe('Ayesha');
    expect(workbook.getWorksheet('Table 1 P1')?.getCell('B2').value).toBe('92');
  });
});
