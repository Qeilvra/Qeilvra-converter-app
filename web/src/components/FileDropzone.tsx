import { useRef, useState } from 'react';
import { CheckCircle2, CloudUpload, File } from 'lucide-react';
import { formatBytes } from '../lib/files';

type Props = {
  accept: string;
  file?: globalThis.File | null;
  files?: globalThis.File[];
  multiple?: boolean;
  capture?: 'user' | 'environment';
  label: string;
  hint: string;
  onFiles: (files: globalThis.File[]) => void;
};

export function FileDropzone({ accept, file, files, multiple, capture, label, hint, onFiles }: Props) {
  const inputRef = useRef<HTMLInputElement>(null);
  const [dragging, setDragging] = useState(false);
  const selected = files ?? (file ? [file] : []);
  const receive = (list: FileList | null) => {
    if (list?.length) onFiles(Array.from(list));
  };
  return (
    <div
      className={`dropzone ${dragging ? 'dropzone--dragging' : ''} ${selected.length ? 'dropzone--selected' : ''}`}
      onDragOver={(event) => { event.preventDefault(); setDragging(true); }}
      onDragLeave={() => setDragging(false)}
      onDrop={(event) => { event.preventDefault(); setDragging(false); receive(event.dataTransfer.files); }}
    >
      <input ref={inputRef} type="file" accept={accept} multiple={multiple} capture={capture} onChange={(event) => { receive(event.target.files); event.currentTarget.value = ''; }} />
      {selected.length ? <CheckCircle2 className="dropzone__icon dropzone__icon--success" /> : <CloudUpload className="dropzone__icon" />}
      <strong>{selected.length ? `${selected.length} file${selected.length > 1 ? 's' : ''} selected` : label}</strong>
      <p>{selected.length ? selected.map((item) => `${item.name} (${formatBytes(item.size)})`).join(' · ') : hint}</p>
      <button type="button" className="button button--secondary" onClick={() => inputRef.current?.click()}>
        <File size={17} /> {selected.length ? 'Change file' : 'Choose file'}
      </button>
    </div>
  );
}
