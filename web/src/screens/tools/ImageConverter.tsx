import { useEffect, useState } from 'react';
import { Image as ImageIcon, RefreshCw } from 'lucide-react';
import { FileDropzone } from '../../components/FileDropzone';
import { ResultCard } from '../../components/ResultCard';
import { StatusBanner } from '../../components/StatusBanner';
import type { ToolDefinition } from '../../data/tools';
import { addHistory, safeBaseName, validateFile } from '../../lib/files';
import { renderImage } from '../../lib/images';
import type { ImageOutputFormat, ProcessingState } from '../../types';
import { ToolLayout } from './ToolLayout';

const formats: Array<{ value: ImageOutputFormat; label: string; ext: string }> = [
  { value: 'image/jpeg', label: 'JPG', ext: 'jpg' }, { value: 'image/png', label: 'PNG', ext: 'png' }, { value: 'image/webp', label: 'WebP', ext: 'webp' }
];

export function ImageConverter({ tool }: { tool: ToolDefinition }) {
  const [file, setFile] = useState<File | null>(null);
  const [format, setFormat] = useState<ImageOutputFormat>('image/jpeg');
  const [quality, setQuality] = useState(90);
  const [blob, setBlob] = useState<Blob | null>(null);
  const [preview, setPreview] = useState('');
  const [state, setState] = useState<ProcessingState>('idle');
  const [error, setError] = useState('');
  useEffect(() => () => { if (preview) URL.revokeObjectURL(preview); }, [preview]);
  const selected = formats.find((item) => item.value === format)!;
  const run = async () => {
    if (!file) return;
    setState('processing'); setBlob(null);
    try {
      const output = await renderImage(file, { type: format, quality: quality / 100, background: format === 'image/jpeg' ? '#ffffff' : undefined });
      if (preview) URL.revokeObjectURL(preview);
      setPreview(URL.createObjectURL(output)); setBlob(output); setState('success');
      addHistory({ operation: 'Image conversion', sourceName: file.name, outputName: `${safeBaseName(file.name)}.${selected.ext}`, outputSize: output.size }, output);
    } catch (reason) { setState('error'); setError(reason instanceof Error ? reason.message : 'Conversion failed.'); }
  };
  return <ToolLayout tool={tool} note="Convert JPG, PNG and WebP images with transparent-background handling.">
    <div className="workflow-grid"><div className="workflow-main">
      <FileDropzone accept={tool.accept} file={file} label="Select an image" hint="JPG, PNG or WebP · up to 30 MB" onFiles={(files) => void (async () => { try { await validateFile(files[0], ['jpeg', 'png', 'webp'], 30); setFile(files[0]); setBlob(null); setState('idle'); } catch (reason) { setState('error'); setError(reason instanceof Error ? reason.message : 'Unsupported file.'); } })()} />
      {file && <section className="control-card"><div className="control-card__title"><RefreshCw /><div><h3>Output settings</h3><p>JPEG replaces transparent areas with white.</p></div></div><div className="segmented-control">{formats.map((item) => <button key={item.value} className={format === item.value ? 'active' : ''} onClick={() => setFormat(item.value)}>{item.label}</button>)}</div>{format !== 'image/png' && <div className="range-row"><label><span>Quality</span><strong>{quality}%</strong></label><input type="range" min="25" max="100" value={quality} onChange={(event) => setQuality(Number(event.target.value))} /></div>}<button className="button button--primary button--full" onClick={() => void run()}><RefreshCw size={18} /> Convert image</button></section>}
      <StatusBanner state={state} error={error} />
    </div><aside className="preview-panel"><h3>Preview</h3>{preview ? <img src={preview} alt="Converted result preview" /> : <div className="preview-placeholder"><ImageIcon /><p>Your converted image will appear here.</p></div>}</aside></div>
    {blob && file && <ResultCard blob={blob} filename={`${safeBaseName(file.name)}.${selected.ext}`} />}
  </ToolLayout>;
}
