import { useState } from 'react';
import { AlertTriangle, FileSpreadsheet, ScanText } from 'lucide-react';
import { FileDropzone } from '../../components/FileDropzone';
import { ResultCard } from '../../components/ResultCard';
import { StatusBanner } from '../../components/StatusBanner';
import type { ToolDefinition } from '../../data/tools';
import { addHistory, safeBaseName, validateFile } from '../../lib/files';
import { extractPdfTables, tablesToXlsx } from '../../lib/pdf';
import type { ExtractedTable, ProcessingState } from '../../types';
import { ToolLayout } from './ToolLayout';

export function PdfToExcel({ tool }: { tool: ToolDefinition }) {
  const [file, setFile] = useState<File | null>(null);
  const [tables, setTables] = useState<ExtractedTable[]>([]);
  const [blob, setBlob] = useState<Blob | null>(null);
  const [state, setState] = useState<ProcessingState>('idle');
  const [error, setError] = useState('');
  const [scanned, setScanned] = useState(false);
  const analyze = async () => {
    if (!file) return;
    setState('processing'); setTables([]); setBlob(null); setScanned(false);
    try {
      const result = await extractPdfTables(file);
      if (result.scanned) { setScanned(true); throw new Error('This appears to be a scanned PDF with no selectable text. OCR is not included in this local browser release.'); }
      if (!result.tables.length) throw new Error('No table-like rows were detected. Try a PDF with selectable text arranged in columns.');
      setTables(result.tables); setState('idle');
    } catch (reason) { setState('error'); setError(reason instanceof Error ? reason.message : 'Table extraction failed.'); }
  };
  const updateCell = (tableId: string, row: number, column: number, value: string) => setTables((current) => current.map((table) => table.id !== tableId ? table : { ...table, rows: table.rows.map((cells, rowIndex) => rowIndex !== row ? cells : cells.map((cell, columnIndex) => columnIndex !== column ? cell : { value, uncertain: false })) }));
  const exportFile = async () => {
    if (!file) return;
    setState('processing'); setError('');
    try {
      const output = await tablesToXlsx(tables); setBlob(output); setState('success');
      addHistory({ operation: 'PDF to Excel', sourceName: file.name, outputName: `${safeBaseName(file.name)}.xlsx`, outputSize: output.size }, output);
    } catch (reason) {
      setState('error'); setError(reason instanceof Error ? reason.message : 'Excel export failed.');
    }
  };
  return <ToolLayout tool={tool} note="Detect table-like text, review uncertain cells and export a valid Excel workbook.">
    <FileDropzone accept={tool.accept} file={file} label="Select a PDF file" hint="Text-based PDF · up to 50 MB" onFiles={(files) => void (async () => { try { await validateFile(files[0], ['pdf']); setFile(files[0]); setTables([]); setBlob(null); setState('idle'); setError(''); } catch (reason) { setState('error'); setError(reason instanceof Error ? reason.message : 'Invalid PDF.'); } })()} />
    {file && tables.length === 0 && state !== 'processing' && <button className="button button--primary button--full tool-action" onClick={() => void analyze()}><ScanText size={19} /> Detect tables</button>}
    <StatusBanner state={state} error={error} />
    {scanned && <div className="status-banner status-banner--warning"><AlertTriangle /><span><strong>OCR needed</strong>Convert the scan to a searchable PDF with a trusted OCR tool, then try again.</span></div>}
    {tables.length > 0 && <section className="table-editor"><div className="table-editor__heading"><div><h3>Review extracted data</h3><p>Yellow cells are uncertain because their row structure differs. Edit any cell before export.</p></div><span>{tables.length} table{tables.length === 1 ? '' : 's'}</span></div>{tables.map((table, tableIndex) => <div className="detected-table" key={table.id}><h4>Table {tableIndex + 1} · page {table.page}</h4><div className="table-scroll"><table><tbody>{table.rows.map((row, rowIndex) => <tr key={`${table.id}-${rowIndex}`}>{row.map((cell, columnIndex) => <td key={`${rowIndex}-${columnIndex}`} className={cell.uncertain ? 'uncertain' : ''}><input aria-label={`Table ${tableIndex + 1}, row ${rowIndex + 1}, column ${columnIndex + 1}`} value={cell.value} onChange={(event) => updateCell(table.id, rowIndex, columnIndex, event.target.value)} /></td>)}</tr>)}</tbody></table></div></div>)}<button className="button button--primary button--full" onClick={() => void exportFile()}><FileSpreadsheet size={19} /> Export Excel workbook</button></section>}
    {blob && file && <ResultCard blob={blob} filename={`${safeBaseName(file.name)}.xlsx`} warning="Table recovery is best-effort. Review formulas, merged cells and column alignment in Excel." />}
  </ToolLayout>;
}
