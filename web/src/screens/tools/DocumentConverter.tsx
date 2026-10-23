import { useEffect, useRef, useState } from 'react';
import { FileOutput } from 'lucide-react';
import { FileDropzone } from '../../components/FileDropzone';
import { ResultCard } from '../../components/ResultCard';
import { StatusBanner } from '../../components/StatusBanner';
import type { ToolDefinition } from '../../data/tools';
import { addHistory, safeBaseName, validateFile } from '../../lib/files';
import { pdfToDocxLocal } from '../../lib/localDocuments';
import { docxToPdfLocal } from '../../lib/localWordPdf';
import { renderDocxOffline } from '../../lib/officeWasm';
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
  const [basicText, setBasicText] = useState(false);
  const [fonts, setFonts] = useState<File[]>([]);
  const active = useRef<AbortController | null>(null);
  useEffect(() => () => active.current?.abort(), []);
  const limitation = direction === 'pdf-word' ? 'Reconstructs editable paragraphs, headings, columns, ruled tables with rectangular merged cells, and positioned images. Original page dimensions and inferred headers/footers are retained. Fonts may be substituted; complex artwork and substantial editing may change layout. Scans require OCR.' : officeService ? `Uploads this file to your configured office renderer at ${configuredOfficeService}. It renders Word layout, including tables, images and pagination. Missing fonts may change the result. No automatic fallback to text export.` : basicText ? 'Plain text export on A4 pages. Original fonts, styling, margins and pagination are discarded. This is not a document-layout conversion. Complex documents are rejected.' : 'LibreOffice renders the DOCX locally, including tables, images, sections and pagination. It requires the packaged offline office engine and a supported browser. Original fonts strongly affect line wrapping: select matching local font files below, or bundled replacement fonts will be used. Word and LibreOffice can still lay out documents differently. No text-only fallback.';
  const isPdf = direction === 'pdf-word';
  const ext = isPdf ? 'docx' : 'pdf';
  const outputName = file ? `${safeBaseName(file.name)}${!isPdf && basicText ? '-text-only' : ''}.${ext}` : '';
  const run = async () => {
    if (!file || state === 'processing') return;
    const controller = new AbortController(); active.current = controller;
    setError('');
    setState('processing'); setBlob(null); setProgress(!isPdf && officeService ? 'Rendering with the selected office service…' : 'Loading local conversion engine…');
    try {
      const output = await (isPdf ? pdfToDocxLocal(file, setProgress) : officeService ? convertDocument(file, 'word-pdf') : basicText ? docxToPdfLocal(file) : renderDocxOffline(file, fonts, setProgress, controller.signal));
      if (controller.signal.aborted) return;
      setBlob(output); setState('success');
      addHistory({ operation: basicText && !isPdf ? 'Word text export' : tool.name, sourceName: file.name, outputName, outputSize: output.size }, output);
    } catch (reason) { if (!controller.signal.aborted) { setState('error'); setError(reason instanceof Error ? reason.message : 'Conversion failed.'); } }
    finally { if (active.current === controller) active.current = null; }
  };
  return <ToolLayout busy={state === 'processing' || state === 'reading'} tool={tool} mode={officeService && !isPdf ? 'service' : 'local'} note={isPdf ? 'Create an editable Word document on this device.' : officeService ? 'Render with your configured office service.' : basicText ? 'Export plain text with basic pagination.' : 'Render the original Word layout on this device.'}>
    <div className="status-banner status-banner--warning"><span><strong>{!isPdf && officeService ? 'Office rendering' : 'Local conversion limits'}</strong>{limitation}</span></div>
    {!isPdf && configuredOfficeService && <label><input type="checkbox" checked={officeService} disabled={state === 'processing'} onChange={event => { setOfficeService(event.target.checked); setBasicText(false); setBlob(null); setState('idle'); }} /> Use configured office renderer (uploads this file to {configuredOfficeService})</label>}
    {!isPdf && <label><input type="checkbox" checked={basicText} onChange={event => { setBasicText(event.target.checked); setOfficeService(false); setBlob(null); setState('idle'); }} /> Export basic text only (discard document layout)</label>}
    {!isPdf && !officeService && !basicText && <label>Matching fonts (optional, stay on this device)<input aria-label="Matching local fonts" type="file" accept=".ttf,.otf" multiple onChange={event => { setFonts(Array.from(event.target.files ?? [])); setBlob(null); setState('idle'); }} /></label>}
    <FileDropzone accept={tool.accept} file={file} label={`Select a ${isPdf ? 'PDF' : 'Word'} file`} hint={`${isPdf ? 'PDF' : 'DOCX'} · up to 20 MB`} onFiles={(files) => void (async () => { try { setFile(null); setBlob(null); setState('reading'); await validateFile(files[0], [isPdf ? 'pdf' : 'docx']); setFile(files[0]); setBlob(null); setState('idle'); } catch (reason) { setState('error'); setError(reason instanceof Error ? reason.message : 'Unsupported file.'); } })()} />
    {file && <button className="button button--primary button--full tool-action" onClick={() => void run()}><FileOutput size={19} /> {!isPdf && basicText ? 'Export basic text PDF' : `Convert to ${ext.toUpperCase()}`}</button>}
    <StatusBanner state={state} error={error} />
    {state === 'processing' && <p role="status">{progress || 'Creating PDF on this device…'}</p>}
    {blob && file && <ResultCard blob={blob} filename={outputName} warning={limitation} />}
  </ToolLayout>;
}
