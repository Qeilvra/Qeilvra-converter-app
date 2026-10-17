import { useState } from 'react';
import { FileOutput } from 'lucide-react';
import { FileDropzone } from '../../components/FileDropzone';
import { ResultCard } from '../../components/ResultCard';
import { StatusBanner } from '../../components/StatusBanner';
import type { ToolDefinition } from '../../data/tools';
import { addHistory, safeBaseName, validateFile } from '../../lib/files';
import { convertDocument, processingApiDescription } from '../../lib/processingApi';
import type { ProcessingState } from '../../types';
import { ToolLayout } from './ToolLayout';

export function DocumentConverter({ tool, direction }: { tool: ToolDefinition; direction: 'pdf-word' | 'word-pdf' }) {
  const [file, setFile] = useState<File | null>(null);
  const [blob, setBlob] = useState<Blob | null>(null);
  const [state, setState] = useState<ProcessingState>('idle');
  const [error, setError] = useState('');
  const isPdf = direction === 'pdf-word';
  const ext = isPdf ? 'docx' : 'pdf';
  const run = async () => {
    if (!file) return;
    setState('processing'); setBlob(null);
    try {
      const output = await convertDocument(file, direction);
      setBlob(output); setState('success');
      addHistory({ operation: tool.name, sourceName: file.name, outputName: `${safeBaseName(file.name)}.${ext}`, outputSize: output.size }, output);
    } catch (reason) { setState('error'); setError(reason instanceof Error ? reason.message : 'Conversion failed.'); }
  };
  return <ToolLayout tool={tool} mode="service" note={isPdf ? 'Reconstruct an editable Word document with a layout-aware engine.' : 'Render Word documents with LibreOffice for faithful pagination and styling.'}>
    <div className="engine-strip"><span className="engine-strip__dot" /><div><strong>High-fidelity processing service</strong><small>{processingApiDescription}</small></div></div>
    <FileDropzone accept={tool.accept} file={file} label={`Select a ${isPdf ? 'PDF' : 'Word'} file`} hint={`${isPdf ? 'PDF' : 'DOCX'} · up to 50 MB`} onFiles={(files) => void (async () => { try { await validateFile(files[0], [isPdf ? 'pdf' : 'docx']); setFile(files[0]); setBlob(null); setState('idle'); } catch (reason) { setState('error'); setError(reason instanceof Error ? reason.message : 'Unsupported file.'); } })()} />
    {file && <button className="button button--primary button--full tool-action" onClick={() => void run()}><FileOutput size={19} /> Convert to {ext.toUpperCase()}</button>}
    <StatusBanner state={state} error={error} mode="service" />
    {blob && file && <ResultCard blob={blob} filename={`${safeBaseName(file.name)}.${ext}`} />}
  </ToolLayout>;
}
