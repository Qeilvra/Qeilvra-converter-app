// @vitest-environment jsdom
import ExcelJS from 'exceljs';
import JSZip from 'jszip';
import { PDFDocument, StandardFonts } from 'pdf-lib';
import { describe, expect, it } from 'vitest';
import { compressPdf, docxToPdf, editPdfPages, mergePdfs, pdfToDocx, splitPdfToZip, tablesToXlsx } from './pdf';

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

  it('produces a parseable optimized PDF and reports the measured result', async () => {
    const input = await samplePdf(2, 'source.pdf');
    const output = await compressPdf(input);
    const parsed = await PDFDocument.load(await output.blob.arrayBuffer());
    expect(parsed.getPageCount()).toBe(2);
    expect(output.reduced).toBe(output.blob.size < input.size);
  });

  it('converts selectable PDF text into a valid DOCX package', async () => {
    const source = await PDFDocument.create();
    const font = await source.embedFont(StandardFonts.Helvetica);
    source.addPage([300, 400]).drawText('FileFlow conversion test', { x: 30, y: 330, font });
    const input = new File([await source.save() as BlobPart], 'text.pdf', { type: 'application/pdf' });
    const output = await pdfToDocx(input);
    const zip = await JSZip.loadAsync(await output.arrayBuffer());
    const document = await zip.file('word/document.xml')?.async('text');
    expect(document).toContain('FileFlow conversion test');
  });

  it('converts readable DOCX XML into a parseable PDF', async () => {
    const zip = new JSZip();
    zip.file('word/document.xml', '<?xml version="1.0"?><w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body><w:p><w:r><w:t>Local Word conversion</w:t></w:r></w:p></w:body></w:document>');
    const input = new File([await zip.generateAsync({ type: 'uint8array' }) as BlobPart], 'source.docx', { type: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document' });
    const output = await docxToPdf(input);
    const parsed = await PDFDocument.load(await output.arrayBuffer());
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
