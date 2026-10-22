import { useEffect, useState } from 'react';
import { BadgeCheck, Image as ImageIcon, Ruler } from 'lucide-react';
import { FileDropzone } from '../../components/FileDropzone';
import { ResultCard } from '../../components/ResultCard';
import { StatusBanner } from '../../components/StatusBanner';
import type { ToolDefinition } from '../../data/tools';
import { addHistory, formatBytes, safeBaseName, validateFile } from '../../lib/files';
import { compressImageToTarget } from '../../lib/images';
import type { ProcessingState } from '../../types';
import { ToolLayout } from './ToolLayout';

const presets = [
  { label: 'Profile photo', width: 600, height: 600, kb: 200 },
  { label: 'Passport style', width: 413, height: 531, kb: 100 },
  { label: 'Signature', width: 600, height: 200, kb: 50 },
  { label: 'Document', width: 1600, height: 2200, kb: 500 }
];

export function PrepareUpload({ tool }: { tool: ToolDefinition }) {
  const [file, setFile] = useState<File | null>(null);
  const [width, setWidth] = useState(600);
  const [height, setHeight] = useState(600);
  const [target, setTarget] = useState(200);
  const [blob, setBlob] = useState<Blob | null>(null);
  const [meta, setMeta] = useState<{ width: number; height: number; reached: boolean } | null>(null);
  const [preview, setPreview] = useState('');
  const [state, setState] = useState<ProcessingState>('idle');
  const [error, setError] = useState('');
  useEffect(() => { setBlob(null); setMeta(null); setPreview(''); }, [file, width, height, target]);
  useEffect(() => () => { if (preview) URL.revokeObjectURL(preview); }, [preview]);
  const run = async () => {
    if (!file) return;
    setState('processing'); setBlob(null);
    try {
      const output = await compressImageToTarget(file, target * 1024, .9, width, height);
      if (preview) URL.revokeObjectURL(preview);
      setPreview(URL.createObjectURL(output.blob)); setBlob(output.blob); setMeta({ width: output.width, height: output.height, reached: output.reachedTarget }); setState('success');
      addHistory({ operation: 'Prepare for Upload', sourceName: file.name, outputName: `${safeBaseName(file.name)}-ready.jpg`, outputSize: output.blob.size }, output.blob);
    } catch (reason) { setState('error'); setError(reason instanceof Error ? reason.message : 'Could not prepare this image.'); }
  };
  return <ToolLayout busy={state === 'processing' || state === 'reading'} tool={tool} note="Prepare a measured image for forms without relying on unverified portal claims.">
    <div className="workflow-grid"><div className="workflow-main">
      <FileDropzone accept={tool.accept} file={file} label="Select a photo or document" hint="JPG, PNG or WebP · processed locally" onFiles={(files) => void (async () => { try { setFile(null); setBlob(null); setMeta(null); setPreview(''); setState('reading'); await validateFile(files[0], ['jpeg', 'png', 'webp'], 30); setFile(files[0]); setBlob(null); setState('idle'); } catch (reason) { setState('error'); setError(reason instanceof Error ? reason.message : 'Unsupported file.'); } })()} />
      {file && <section className="control-card"><div className="control-card__title"><Ruler /><div><h3>Output requirements</h3><p>Presets are editable starting points. Check your form’s current rules.</p></div></div><div className="preset-row">{presets.map((preset) => <button key={preset.label} onClick={() => { setWidth(preset.width); setHeight(preset.height); setTarget(preset.kb); }}><strong>{preset.label}</strong><small>{preset.width}×{preset.height} · {preset.kb} KB</small></button>)}</div><div className="field-grid field-grid--three"><label className="field"><span>Max width (px)</span><input type="number" value={width} min="100" onChange={(event) => setWidth(Number(event.target.value))} /></label><label className="field"><span>Max height (px)</span><input type="number" value={height} min="100" onChange={(event) => setHeight(Number(event.target.value))} /></label><label className="field"><span>Max size (KB)</span><input type="number" value={target} min="10" onChange={(event) => setTarget(Number(event.target.value))} /></label></div><button className="button button--primary button--full" onClick={() => void run()}><BadgeCheck size={18} /> Prepare file</button></section>}
      <StatusBanner state={state} error={error} />
    </div><aside className="preview-panel"><h3>Result check</h3>{preview && meta ? <><img src={preview} alt="Prepared file preview" /><div className="verification-list"><span className={meta.reached ? 'pass' : 'fail'}><BadgeCheck /> Size <strong>{blob && formatBytes(blob.size)}</strong></span><span className="pass"><BadgeCheck /> Dimensions <strong>{meta.width} × {meta.height}</strong></span></div></> : <div className="preview-placeholder"><ImageIcon /><p>The verified dimensions and actual file size will appear here.</p></div>}</aside></div>
    {blob && file && <ResultCard blob={blob} filename={`${safeBaseName(file.name)}-ready.jpg`} warning={!meta?.reached ? 'The requested maximum was not reached at the minimum readability setting.' : undefined} />}
  </ToolLayout>;
}
