import { useState } from 'react';
import { ArrowDown, ArrowLeft, ArrowRight, ArrowUp, Combine, FileArchive, RotateCw, Scissors, Trash2 } from 'lucide-react';
import { FileDropzone } from '../../components/FileDropzone';
import { ResultCard } from '../../components/ResultCard';
import { StatusBanner } from '../../components/StatusBanner';
import type { ToolDefinition } from '../../data/tools';
import { addHistory, safeBaseName, validateFile } from '../../lib/files';
import { editPdfPages, getPdfPageCount, mergePdfs, splitPdfToZip } from '../../lib/pdf';
import type { ProcessingState } from '../../types';
import { ToolLayout } from './ToolLayout';

type PageItem = { page: number; rotation: number };

export function PdfEditor({ tool }: { tool: ToolDefinition }) {
  const [files, setFiles] = useState<File[]>([]);
  const [pages, setPages] = useState<PageItem[]>([]);
  const [blob, setBlob] = useState<Blob | null>(null);
  const [resultName, setResultName] = useState('');
  const [state, setState] = useState<ProcessingState>('idle');
  const [error, setError] = useState('');
  const choose = async (next: File[]) => {
    setState('reading'); setBlob(null);
    try {
      for (const file of next) await validateFile(file, ['pdf']);
      if (next.length > 20) throw new Error('Select at most 20 PDFs at once.');
      if (next.reduce((sum, file) => sum + file.size, 0) > 150 * 1024 * 1024) throw new Error('The selected PDFs exceed the 150 MB combined limit.');
      setFiles(next); setResultName(''); setError('');
      if (next.length === 1) {
        const count = await getPdfPageCount(next[0]);
        setPages(Array.from({ length: count }, (_, index) => ({ page: index + 1, rotation: 0 })));
      } else setPages([]);
      setState('idle');
    } catch (reason) { setState('error'); setError(reason instanceof Error ? reason.message : 'Could not open these PDFs.'); }
  };
  const move = (index: number, delta: number) => setPages((current) => { const target = index + delta; if (target < 0 || target >= current.length) return current; const next = [...current]; [next[index], next[target]] = [next[target], next[index]]; return next; });
  const moveFile = (index: number, delta: number) => setFiles((current) => { const target = index + delta; if (target < 0 || target >= current.length) return current; const next = [...current]; [next[index], next[target]] = [next[target], next[index]]; return next; });
  const run = async () => {
    if (!files.length) return;
    setState('processing'); setBlob(null);
    try {
      const output = files.length > 1 ? await mergePdfs(files) : await editPdfPages(files[0], pages.map((item) => item.page), Object.fromEntries(pages.map((item) => [item.page, item.rotation])));
      setBlob(output); setState('success');
      const outputName = files.length > 1 ? 'merged.pdf' : `${safeBaseName(files[0].name)}-edited.pdf`;
      setResultName(outputName);
      addHistory({ operation: files.length > 1 ? 'Merge PDFs' : 'Edit PDF pages', sourceName: files.length > 1 ? `${files.length} PDFs` : files[0].name, outputName, outputSize: output.size }, output);
    } catch (reason) { setState('error'); setError(reason instanceof Error ? reason.message : 'PDF editing failed.'); }
  };
  const split = async () => {
    if (files.length !== 1) return;
    setState('processing'); setBlob(null); setError('');
    try {
      const output = await splitPdfToZip(files[0]);
      const outputName = `${safeBaseName(files[0].name)}-pages.zip`;
      setBlob(output); setResultName(outputName); setState('success');
      addHistory({ operation: 'Split PDF', sourceName: files[0].name, outputName, outputSize: output.size }, output);
    } catch (reason) { setState('error'); setError(reason instanceof Error ? reason.message : 'PDF splitting failed.'); }
  };
  return <ToolLayout tool={tool} note="Merge files, reorder, rotate, extract or delete pages without changing the originals.">
    <FileDropzone accept={tool.accept} files={files} multiple label="Select one or more PDFs" hint="One PDF to edit pages · multiple PDFs to merge" onFiles={(next) => void choose(next)} />
    {files.length > 1 && <section className="control-card"><div className="control-card__title"><Combine /><div><h3>Merge {files.length} PDFs</h3><p>Change the order below, then combine every page.</p></div></div><ol className="file-order">{files.map((file, index) => <li key={`${file.name}-${file.lastModified}-${index}`}><span>{file.name}</span><span className="file-order__actions"><button disabled={index === 0} onClick={() => moveFile(index, -1)} aria-label={`Move ${file.name} up`}><ArrowUp /></button><button disabled={index === files.length - 1} onClick={() => moveFile(index, 1)} aria-label={`Move ${file.name} down`}><ArrowDown /></button></span></li>)}</ol><button className="button button--primary button--full" onClick={() => void run()}><Combine size={18} /> Merge PDFs</button></section>}
    {files.length === 1 && pages.length > 0 && <section className="control-card"><div className="control-card__title"><Scissors /><div><h3>Arrange or split pages</h3><p>Remove unwanted pages, rotate them, change their order, or export every original page.</p></div></div><div className="page-organizer">{pages.map((item, index) => <article key={item.page}><div className="page-paper" style={{ transform: `rotate(${item.rotation}deg)` }}><span>{item.page}</span></div><strong>Page {item.page}</strong><small>{item.rotation}°</small><div><button disabled={index === 0} onClick={() => move(index, -1)} aria-label={`Move page ${item.page} left`}><ArrowLeft /></button><button disabled={index === pages.length - 1} onClick={() => move(index, 1)} aria-label={`Move page ${item.page} right`}><ArrowRight /></button><button onClick={() => setPages((current) => current.map((page) => page.page === item.page ? { ...page, rotation: (page.rotation + 90) % 360 } : page))} aria-label={`Rotate page ${item.page}`}><RotateCw /></button><button disabled={pages.length === 1} onClick={() => setPages((current) => current.filter((page) => page.page !== item.page))} aria-label={`Delete page ${item.page}`}><Trash2 /></button></div></article>)}</div><div className="action-row"><button className="button button--secondary" onClick={() => void split()}><FileArchive size={18} /> Split pages to ZIP</button><button className="button button--primary" onClick={() => void run()}><Scissors size={18} /> Save edited PDF</button></div></section>}
    <StatusBanner state={state} error={error} />
    {blob && resultName && <ResultCard blob={blob} filename={resultName} />}
  </ToolLayout>;
}
