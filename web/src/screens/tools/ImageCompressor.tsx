import { useEffect, useState } from 'react';
import { Gauge, Image as ImageIcon, SlidersHorizontal } from 'lucide-react';
import { FileDropzone } from '../../components/FileDropzone';
import { ResultCard } from '../../components/ResultCard';
import { StatusBanner } from '../../components/StatusBanner';
import type { ToolDefinition } from '../../data/tools';
import { addHistory, formatBytes, safeBaseName, validateFile } from '../../lib/files';
import { compressImageToTarget, decodeImage, type CompressionResult } from '../../lib/images';
import type { ProcessingState } from '../../types';
import { ToolLayout } from './ToolLayout';

const targets = [20, 50, 100, 200, 500];

export function ImageCompressor({ tool }: { tool: ToolDefinition }) {
  const [file, setFile] = useState<File | null>(null);
  const [targetKb, setTargetKb] = useState(100);
  const [custom, setCustom] = useState(false);
  const [quality, setQuality] = useState(82);
  const [maxWidth, setMaxWidth] = useState(1920);
  const [dimensions, setDimensions] = useState('');
  const [result, setResult] = useState<CompressionResult | null>(null);
  const [preview, setPreview] = useState('');
  const [state, setState] = useState<ProcessingState>('idle');
  const [error, setError] = useState('');

  useEffect(() => () => { if (preview) URL.revokeObjectURL(preview); }, [preview]);

  const choose = async ([next]: File[]) => {
    try {
      await validateFile(next, ['jpeg', 'png', 'webp'], 30);
      const decoded = await decodeImage(next);
      setDimensions(`${decoded.width} × ${decoded.height} px`);
      URL.revokeObjectURL(decoded.url);
      setFile(next); setResult(null); setState('idle'); setError('');
    } catch (reason) { setState('error'); setError(reason instanceof Error ? reason.message : 'Unsupported image.'); }
  };

  const run = async () => {
    if (!file) return;
    setState('processing'); setError(''); setResult(null);
    try {
      if (!Number.isFinite(targetKb) || targetKb < 10 || targetKb > 10240) throw new Error('Choose a target between 10 KB and 10 MB.');
      const output = await compressImageToTarget(file, targetKb * 1024, quality / 100, maxWidth, maxWidth);
      const url = URL.createObjectURL(output.blob);
      if (preview) URL.revokeObjectURL(preview);
      setPreview(url); setResult(output); setState('success');
      addHistory({ operation: 'Image compression', sourceName: file.name, outputName: `${safeBaseName(file.name)}-compressed.jpg`, outputSize: output.blob.size }, output.blob);
    } catch (reason) { setState('error'); setError(reason instanceof Error ? reason.message : 'Compression failed.'); }
  };

  const outputName = file ? `${safeBaseName(file.name)}-compressed.jpg` : 'compressed.jpg';
  return <ToolLayout tool={tool} note="Compress images to a measured maximum size, right in your browser.">
    <div className="workflow-grid">
      <div className="workflow-main">
        <FileDropzone accept={tool.accept} file={file} label="Select an image" hint="JPG, PNG or WebP · up to 30 MB" onFiles={(files) => void choose(files)} />
        {file && <section className="control-card">
          <div className="control-card__title"><Gauge /><div><h3>Choose target size</h3><p>FileFlow will search quality and dimensions to stay under it.</p></div></div>
          <div className="target-grid">{targets.map((value) => <button type="button" key={value} className={!custom && targetKb === value ? 'active' : ''} onClick={() => { setTargetKb(value); setCustom(false); }}><strong>{value} KB</strong><small>{value <= 50 ? 'Forms' : value <= 200 ? 'Applications' : 'High quality'}</small></button>)}<button type="button" className={custom ? 'active' : ''} onClick={() => setCustom(true)}><strong>Custom</strong><small>KB</small></button></div>
          {custom && <label className="field"><span>Maximum file size (KB)</span><input type="number" min="10" max="10240" value={targetKb} onChange={(event) => setTargetKb(Number(event.target.value))} /></label>}
          <div className="range-row"><label><span>Starting quality</span><strong>{quality}%</strong></label><input type="range" min="25" max="95" value={quality} onChange={(event) => setQuality(Number(event.target.value))} /></div>
          <div className="field-grid"><label className="field"><span>Maximum width</span><select value={maxWidth} onChange={(event) => setMaxWidth(Number(event.target.value))}><option value="960">960 px</option><option value="1280">1280 px</option><option value="1920">1920 px</option><option value="2560">2560 px</option><option value="4096">Keep up to 4096 px</option></select></label><div className="metric"><SlidersHorizontal /><span>Original</span><strong>{dimensions}</strong></div></div>
          <button className="button button--primary button--full" type="button" onClick={() => void run()}><ImageIcon size={19} /> Compress image</button>
        </section>}
        <StatusBanner state={state} error={error} />
      </div>
      <aside className="preview-panel">
        <h3>Preview</h3>
        {preview ? <><img src={preview} alt="Compressed result preview" /><div className="comparison"><span><small>Before</small><strong>{file && formatBytes(file.size)}</strong></span><span><small>After</small><strong>{result && formatBytes(result.blob.size)}</strong></span></div></> : <div className="preview-placeholder"><ImageIcon /><p>Your compressed image will appear here.</p></div>}
      </aside>
    </div>
    {result && <ResultCard blob={result.blob} filename={outputName} warning={!result.reachedTarget ? `Could not reach ${targetKb} KB without reducing below a readable minimum. Actual size is ${formatBytes(result.blob.size)}.` : undefined} />}
  </ToolLayout>;
}
