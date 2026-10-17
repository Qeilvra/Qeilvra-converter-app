import { useEffect, useRef, useState } from 'react';
import { ArrowDown, ArrowUp, Crop, FileOutput, RotateCw, Trash2 } from 'lucide-react';
import { FileDropzone } from '../../components/FileDropzone';
import { ResultCard } from '../../components/ResultCard';
import { StatusBanner } from '../../components/StatusBanner';
import type { ToolDefinition } from '../../data/tools';
import { addHistory, safeBaseName, validateFile } from '../../lib/files';
import { imagesToPdf, renderImage } from '../../lib/images';
import type { ProcessingState } from '../../types';
import { ToolLayout } from './ToolLayout';

type CropEdges = { left: number; top: number; right: number; bottom: number };
type ImageEntry = { id: string; file: File; rotation: 0 | 90 | 180 | 270; crop: CropEdges; preview: string };
const EMPTY_CROP: CropEdges = { left: 0, top: 0, right: 0, bottom: 0 };

export function ImageToPdf({ tool, scanner = false }: { tool: ToolDefinition; scanner?: boolean }) {
  const [items, setItems] = useState<ImageEntry[]>([]);
  const [pageSize, setPageSize] = useState<'a4' | 'letter' | 'fit'>('a4');
  const [margin, setMargin] = useState(10);
  const [blob, setBlob] = useState<Blob | null>(null);
  const [state, setState] = useState<ProcessingState>('idle');
  const [error, setError] = useState('');
  const [editingId, setEditingId] = useState<string | null>(null);
  const previewUrls = useRef(new Set<string>());
  useEffect(() => () => previewUrls.current.forEach((url) => URL.revokeObjectURL(url)), []);
  const addFiles = async (files: File[]) => {
    try {
      if (items.length + files.length > 50) throw new Error('Add no more than 50 pages to one PDF.');
      const totalBytes = [...items.map((item) => item.file), ...files].reduce((sum, file) => sum + file.size, 0);
      if (totalBytes > 120 * 1024 * 1024) throw new Error('The combined images exceed the 120 MB browser session limit.');
      for (const file of files) await validateFile(file, ['jpeg', 'png', 'webp'], 30);
      const added = files.map((file) => {
        const preview = URL.createObjectURL(file);
        previewUrls.current.add(preview);
        return { id: crypto.randomUUID(), file, rotation: 0 as const, crop: { ...EMPTY_CROP }, preview };
      });
      setItems((current) => [...current, ...added]);
      setBlob(null); setState('idle');
    } catch (reason) { setState('error'); setError(reason instanceof Error ? reason.message : 'Unsupported image.'); }
  };
  const move = (index: number, delta: number) => setItems((current) => { const next = [...current]; const target = index + delta; if (target < 0 || target >= next.length) return current; [next[index], next[target]] = [next[target], next[index]]; return next; });
  const remove = (id: string) => setItems((current) => { const match = current.find((item) => item.id === id); if (match) { URL.revokeObjectURL(match.preview); previewUrls.current.delete(match.preview); } return current.filter((item) => item.id !== id); });
  const rotate = (id: string) => setItems((current) => current.map((item) => item.id === id ? { ...item, rotation: ((item.rotation + 90) % 360) as ImageEntry['rotation'] } : item));
  const updateCrop = (id: string, edge: keyof CropEdges, percent: number) => setItems((current) => current.map((item) => item.id === id ? { ...item, crop: { ...item.crop, [edge]: percent / 100 } } : item));
  const run = async () => {
    setState('processing'); setBlob(null);
    try {
      const prepared: File[] = [];
      for (const item of items) {
        const rendered = await renderImage(item.file, { type: 'image/jpeg', quality: .94, rotation: item.rotation, crop: item.crop, background: '#ffffff', maxWidth: 3200, maxHeight: 3200 });
        prepared.push(new File([rendered], `${safeBaseName(item.file.name)}.jpg`, { type: 'image/jpeg' }));
      }
      const output = await imagesToPdf(prepared, pageSize, margin);
      setBlob(output); setState('success');
      addHistory({ operation: scanner ? 'Document scan' : 'Image to PDF', sourceName: `${items.length} image${items.length === 1 ? '' : 's'}`, outputName: scanner ? 'fileflow-scan.pdf' : 'images.pdf', outputSize: output.size }, output);
    } catch (reason) { setState('error'); setError(reason instanceof Error ? reason.message : 'PDF creation failed.'); }
  };
  return <ToolLayout tool={tool} note={scanner ? 'Capture document pages with your camera, arrange them and export a real PDF.' : 'Arrange multiple images and create a real multipage PDF.'}>
    <FileDropzone accept={scanner ? 'image/*' : tool.accept} files={items.map((item) => item.file)} multiple capture={scanner ? 'environment' : undefined} label={scanner ? 'Capture document pages' : 'Select your images'} hint={scanner ? 'Uses the rear camera on supported mobile browsers; you can also select existing images' : 'JPG, PNG or WebP · select more than one'} onFiles={(files) => void addFiles(files)} />
    {scanner && <div className="limitation-card"><strong>Manual edge correction</strong><p>After capture, use Crop on each page to trim the four edges. Automatic perspective correction is not available in this browser build, so photograph pages as squarely as possible.</p></div>}
    {items.length > 0 && <div className="image-pages">{items.map((item, index) => <article key={item.id}><div className="image-pages__thumb"><img src={item.preview} alt={`Page ${index + 1}`} style={{ transform: `rotate(${item.rotation}deg)`, clipPath: `inset(${item.crop.top * 100}% ${item.crop.right * 100}% ${item.crop.bottom * 100}% ${item.crop.left * 100}%)` }} /><span>{index + 1}</span></div><div className="image-pages__copy"><strong>{item.file.name}</strong><small>Page {index + 1} · {item.rotation}°{Object.values(item.crop).some(Boolean) ? ' · cropped' : ''}</small></div><div className="image-pages__actions"><button onClick={() => move(index, -1)} disabled={index === 0} aria-label="Move up"><ArrowUp /></button><button onClick={() => move(index, 1)} disabled={index === items.length - 1} aria-label="Move down"><ArrowDown /></button><button onClick={() => setEditingId(editingId === item.id ? null : item.id)} aria-label={`Crop page ${index + 1}`}><Crop /></button><button onClick={() => rotate(item.id)} aria-label="Rotate"><RotateCw /></button><button onClick={() => { if (editingId === item.id) setEditingId(null); remove(item.id); }} aria-label="Remove"><Trash2 /></button></div></article>)}</div>}
    {editingId && (() => { const item = items.find((entry) => entry.id === editingId); if (!item) return null; return <section className="control-card crop-editor"><div className="control-card__title"><Crop /><div><h3>Crop page {items.indexOf(item) + 1}</h3><p>Trim each edge. The preview above updates before export.</p></div></div>{(['top', 'right', 'bottom', 'left'] as const).map((edge) => <div className="range-row" key={edge}><label><span>{edge} edge</span><strong>{Math.round(item.crop[edge] * 100)}%</strong></label><input type="range" min="0" max="45" value={Math.round(item.crop[edge] * 100)} onChange={(event) => updateCrop(item.id, edge, Number(event.target.value))} /></div>)}<button className="button button--secondary" type="button" onClick={() => setItems((current) => current.map((entry) => entry.id === item.id ? { ...entry, crop: { ...EMPTY_CROP } } : entry))}>Reset crop</button></section>; })()}
    {items.length > 0 && <section className="control-card"><div className="field-grid"><label className="field"><span>Page size</span><select value={pageSize} onChange={(event) => setPageSize(event.target.value as typeof pageSize)}><option value="a4">A4</option><option value="letter">US Letter</option><option value="fit">Fit to each image</option></select></label><label className="field"><span>Margin</span><select value={margin} onChange={(event) => setMargin(Number(event.target.value))}><option value="0">None</option><option value="5">5 mm</option><option value="10">10 mm</option><option value="15">15 mm</option></select></label></div><button className="button button--primary button--full" onClick={() => void run()}><FileOutput size={18} /> Create PDF</button></section>}
    <StatusBanner state={state} error={error} />
    {blob && <ResultCard blob={blob} filename={scanner ? 'fileflow-scan.pdf' : 'images.pdf'} />}
  </ToolLayout>;
}
