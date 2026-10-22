import { useEffect, useState } from 'react';
import { Archive } from 'lucide-react';
import { FileDropzone } from '../../components/FileDropzone';
import { ResultCard } from '../../components/ResultCard';
import { StatusBanner } from '../../components/StatusBanner';
import type { ToolDefinition } from '../../data/tools';
import { addHistory, formatBytes, safeBaseName, validateFile } from '../../lib/files';
import type { PdfCompressionResult } from '../../lib/processingApi';
import { compressPdfLocal } from '../../lib/localDocuments';
import type { ProcessingState } from '../../types';
import { ToolLayout } from './ToolLayout';

export function PdfCompressor({ tool }: { tool: ToolDefinition }) {
  const [file, setFile] = useState<File | null>(null);
  const [result, setResult] = useState<PdfCompressionResult | null>(null);
  const [state, setState] = useState<ProcessingState>('idle');
  const [error, setError] = useState('');
  useEffect(() => { setResult(null); }, [file]);
  const run = async () => {
    if (!file) return;
    setState('processing'); setResult(null);
    try { const output = await compressPdfLocal(file); setResult(output); setState('success'); if (output.reduced) addHistory({ operation: 'PDF compression', sourceName: file.name, outputName: `${safeBaseName(file.name)}-compressed.pdf`, outputSize: output.blob.size }, output.blob); }
    catch (reason) { setState('error'); setError(reason instanceof Error ? reason.message : 'PDF optimization failed.'); }
  };
  const reduction = result && file ? Math.max(0, Math.round((1 - result.blob.size / file.size) * 100)) : 0;
  return <ToolLayout busy={state === 'processing' || state === 'reading'} tool={tool} note="Optimize PDF object structure locally. Text and images stay intact. Image-heavy PDFs may not shrink; no rasterization or image recompression is performed.">
    <FileDropzone accept={tool.accept} file={file} label="Select a PDF" hint="Unencrypted PDF · up to 20 MB" onFiles={(files) => void (async () => { try { setFile(null); setResult(null); setState('reading'); await validateFile(files[0], ['pdf']); setFile(files[0]); setResult(null); setState('idle'); } catch (reason) { setState('error'); setError(reason instanceof Error ? reason.message : 'Invalid PDF.'); } })()} />
    {file && <section className="control-card"><button className="button button--primary button--full tool-action" onClick={() => void run()}><Archive size={19} /> Compress PDF</button></section>}
    <StatusBanner state={state} error={error} />
    {result && file && result.reduced && <ResultCard blob={result.blob} filename={`${safeBaseName(file.name)}-compressed.pdf`} warning={`${formatBytes(file.size)} → ${formatBytes(result.blob.size)} · ${reduction}% smaller · ${result.imagesOptimized} embedded image${result.imagesOptimized === 1 ? '' : 's'} optimized.`} />}
    {result && file && !result.reduced && <div className="status-banner status-banner--warning"><span><strong>No safe reduction found</strong>The optimized result was not smaller, so FileFlow kept your original instead of claiming a reduction.</span></div>}
  </ToolLayout>;
}
