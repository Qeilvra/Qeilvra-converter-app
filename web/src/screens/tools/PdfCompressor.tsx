import { useState } from 'react';
import { Archive } from 'lucide-react';
import { FileDropzone } from '../../components/FileDropzone';
import { ResultCard } from '../../components/ResultCard';
import { StatusBanner } from '../../components/StatusBanner';
import type { ToolDefinition } from '../../data/tools';
import { addHistory, formatBytes, safeBaseName, validateFile } from '../../lib/files';
import { compressPdf } from '../../lib/pdf';
import type { ProcessingState } from '../../types';
import { ToolLayout } from './ToolLayout';

export function PdfCompressor({ tool }: { tool: ToolDefinition }) {
  const [file, setFile] = useState<File | null>(null);
  const [result, setResult] = useState<{ blob: Blob; reduced: boolean } | null>(null);
  const [state, setState] = useState<ProcessingState>('idle');
  const [error, setError] = useState('');
  const run = async () => {
    if (!file) return;
    setState('processing'); setResult(null);
    try { const output = await compressPdf(file); setResult(output); setState('success'); addHistory({ operation: 'PDF compression', sourceName: file.name, outputName: `${safeBaseName(file.name)}-optimized.pdf`, outputSize: output.blob.size }, output.blob); }
    catch (reason) { setState('error'); setError(reason instanceof Error ? reason.message : 'PDF optimization failed.'); }
  };
  return <ToolLayout tool={tool} note="Rebuild PDF structure with object streams without rasterizing pages.">
    <div className="limitation-card"><strong>Lossless structural optimization</strong><p>Browser processing preserves page content, fonts and forms where possible. Image-heavy PDFs usually need image recompression, which this safe local pass does not attempt.</p></div>
    <FileDropzone accept={tool.accept} file={file} label="Select a PDF" hint="Unencrypted PDF · up to 50 MB" onFiles={(files) => void (async () => { try { await validateFile(files[0], ['pdf']); setFile(files[0]); setResult(null); setState('idle'); } catch (reason) { setState('error'); setError(reason instanceof Error ? reason.message : 'Invalid PDF.'); } })()} />
    {file && <button className="button button--primary button--full tool-action" onClick={() => void run()}><Archive size={19} /> Optimize PDF</button>}
    <StatusBanner state={state} error={error} />
    {result && file && <ResultCard blob={result.blob} filename={`${safeBaseName(file.name)}-optimized.pdf`} warning={result.reduced ? `Reduced from ${formatBytes(file.size)} to ${formatBytes(result.blob.size)}.` : `This PDF is already structurally optimized. The measured output (${formatBytes(result.blob.size)}) is not smaller than the original.`} />}
  </ToolLayout>;
}
