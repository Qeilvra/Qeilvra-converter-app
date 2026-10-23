import { useState } from 'react';
import { FileOutput } from 'lucide-react';
import { FileDropzone } from '../../components/FileDropzone';
import { ResultCard } from '../../components/ResultCard';
import { StatusBanner } from '../../components/StatusBanner';
import type { ToolDefinition } from '../../data/tools';
import { addHistory, safeBaseName, validateFile } from '../../lib/files';
import { pdfToDocxLocal } from '../../lib/localDocuments';
import { docxToPdfLocal } from '../../lib/localWordPdf';
import { configuredOfficeService, convertDocument } from '../../lib/processingApi';
import type { ProcessingState } from '../../types';
import { ToolLayout } from './ToolLayout';

export function DocumentConverter({ tool, direction }: { tool: ToolDefinition; direction: 'pdf-word' | 'word-pdf' }) {
  const [file, setFile] = useState<File | null>(null);
  const [blob, setBlob] = useState<Blob | null>(null);
  const [state, setState] = useState<ProcessingState>('idle');
  const [error, setError] = useState('');
  const [progress, setProgress] = useState('');
  const [officeService, setOfficeService] = useState(false);
  const limitation = direction === 'pdf-word' ? 'Reconstructs editable paragraphs, headings, columns, ruled tables with rectangular merged cells, and positioned images. Original page dimensions and inferred headers/footers are retained. Fonts may be substituted; complex artwork and substantial editing may change layout. Scans require OCR.' : officeService ? `Uploads this file to your configured office renderer at ${configuredOfficeService}. It renders Word layout, including tables, images and pagination. Missing fonts may change the result. No automatic fallback to text export.` : 'Basic text export on A4 pages. Original fonts, styling and pagination are lost. For accurate conversion, open the DOCX in an installed office app such as Collabora Office, Word or LibreOffice and choose Export as PDF. Tables, images, fields and headers require an office renderer.';
  const isPdf = direction === 'pdf-word';
  const ext = isPdf ? 'docx' : 'pdf';
  const run = async () => {
    if (!file) return;
    setState('processing'); setBlob(null); setProgress(!isPdf && officeService ? 'Rendering with the selected office service…' : 'Loading local conversion engine…');
    try {
      const output = await (isPdf ? pdfToDocxLocal(file, setProgress) : officeService ? convertDocument(file, 'word-pdf') : docxToPdfLocal(file));
      setBlob(output); setState('success');
      addHistory({ operation: tool.name, sourceName: file.name, outputName: `${safeBaseName(file.name)}.${ext}`, outputSize: output.size }, output);
    } catch (reason) { setState('error'); setError(reason instanceof Error ? reason.message : 'Conversion failed.'); }
  };
  return <ToolLayout busy={state === 'processing' || state === 'reading'} tool={tool} note={isPdf ? 'Create an editable Word document on this device.' : officeService ? 'Render with your configured office service.' : 'Export simple Word text locally with basic pagination.'}>
    <div className="status-banner status-banner--warning"><span><strong>{!isPdf && officeService ? 'Office rendering' : 'Local conversion limits'}</strong>{limitation}</span></div>
    {!isPdf && configuredOfficeService && <label><input type="checkbox" checked={officeService} disabled={state === 'processing'} onChange={event => { setOfficeService(event.target.checked); setBlob(null); setState('idle'); }} /> Use configured office renderer (uploads this file to {configuredOfficeService})</label>}
    <FileDropzone accept={tool.accept} file={file} label={`Select a ${isPdf ? 'PDF' : 'Word'} file`} hint={`${isPdf ? 'PDF' : 'DOCX'} · up to 20 MB`} onFiles={(files) => void (async () => { try { setFile(null); setBlob(null); setState('reading'); await validateFile(files[0], [isPdf ? 'pdf' : 'docx']); setFile(files[0]); setBlob(null); setState('idle'); } catch (reason) { setState('error'); setError(reason instanceof Error ? reason.message : 'Unsupported file.'); } })()} />
    {file && <button className="button button--primary button--full tool-action" onClick={() => void run()}><FileOutput size={19} /> {isPdf || officeService ? `Convert to ${ext.toUpperCase()}` : 'Export basic text PDF'}</button>}
    <StatusBanner state={state} error={error} />
    {state === 'processing' && <p role="status">{progress || 'Creating PDF on this device…'}</p>}
    {blob && file && <ResultCard blob={blob} filename={`${safeBaseName(file.name)}.${ext}`} warning={limitation} />}
  </ToolLayout>;
}
