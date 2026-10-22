import { useState } from 'react';
import { FileOutput } from 'lucide-react';
import { FileDropzone } from '../../components/FileDropzone';
import { ResultCard } from '../../components/ResultCard';
import { StatusBanner } from '../../components/StatusBanner';
import type { ToolDefinition } from '../../data/tools';
import { addHistory, safeBaseName, validateFile } from '../../lib/files';
import { pdfToDocxLocal } from '../../lib/localDocuments';
import { docxToPdfLocal } from '../../lib/localWordPdf';
import type { ProcessingState } from '../../types';
import { ToolLayout } from './ToolLayout';

export function DocumentConverter({ tool, direction }: { tool: ToolDefinition; direction: 'pdf-word' | 'word-pdf' }) {
  const [file, setFile] = useState<File | null>(null);
  const [blob, setBlob] = useState<Blob | null>(null);
  const [state, setState] = useState<ProcessingState>('idle');
  const [error, setError] = useState('');
  const [progress, setProgress] = useState('');
  const limitation = direction === 'pdf-word' ? 'Editable text, approximate font sizes, page dimensions, simple tables and embedded bitmap images are recovered locally. Lines remain separate paragraphs. Columns, merged cells, vector artwork, exact fonts and headers/footers may need correction. Scans require OCR.' : 'Local text-only reflow on A4 pages. Original fonts, styling and pagination are not preserved. Documents with tables, images, fields or headers are rejected. Use Word or LibreOffice on your device for full rendering.';
  const isPdf = direction === 'pdf-word';
  const ext = isPdf ? 'docx' : 'pdf';
  const run = async () => {
    if (!file) return;
    setState('processing'); setBlob(null); setProgress('Loading local conversion engine…');
    try {
      const output = await (isPdf ? pdfToDocxLocal(file, setProgress) : docxToPdfLocal(file));
      setBlob(output); setState('success');
      addHistory({ operation: tool.name, sourceName: file.name, outputName: `${safeBaseName(file.name)}.${ext}`, outputSize: output.size }, output);
    } catch (reason) { setState('error'); setError(reason instanceof Error ? reason.message : 'Conversion failed.'); }
  };
  return <ToolLayout busy={state === 'processing' || state === 'reading'} tool={tool} note={isPdf ? 'Create an editable Word document on this device.' : 'Export simple Word text locally with basic pagination.'}>
    <div className="status-banner status-banner--warning"><span><strong>Local conversion limits</strong>{limitation}</span></div>
    <FileDropzone accept={tool.accept} file={file} label={`Select a ${isPdf ? 'PDF' : 'Word'} file`} hint={`${isPdf ? 'PDF' : 'DOCX'} · up to 20 MB`} onFiles={(files) => void (async () => { try { setFile(null); setBlob(null); setState('reading'); await validateFile(files[0], [isPdf ? 'pdf' : 'docx']); setFile(files[0]); setBlob(null); setState('idle'); } catch (reason) { setState('error'); setError(reason instanceof Error ? reason.message : 'Unsupported file.'); } })()} />
    {file && <button className="button button--primary button--full tool-action" onClick={() => void run()}><FileOutput size={19} /> Convert to {ext.toUpperCase()}</button>}
    <StatusBanner state={state} error={error} />
    {state === 'processing' && <p role="status">{progress || 'Creating PDF on this device…'}</p>}
    {blob && file && <ResultCard blob={blob} filename={`${safeBaseName(file.name)}.${ext}`} warning={limitation} />}
  </ToolLayout>;
}
