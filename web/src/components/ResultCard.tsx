import { useState } from 'react';
import { Check, Download, Share2, TriangleAlert } from 'lucide-react';
import { canShareFile, downloadBlob, formatBytes, shareBlob } from '../lib/files';

export function ResultCard({ blob, filename, warning }: { blob: Blob; filename: string; warning?: string }) {
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const save = () => {
    setError('');
    downloadBlob(blob, filename);
    setMessage('Download requested. Confirm the save in your browser.');
  };
  const share = async () => {
    setMessage(''); setError('');
    try {
      await shareBlob(blob, filename);
      setMessage('Shared successfully.');
    } catch (reason) {
      if (reason instanceof DOMException && reason.name === 'AbortError') return;
      setError(reason instanceof Error ? reason.message : 'Sharing failed. Use Save instead.');
    }
  };
  return (
    <section className="result-card" aria-live="polite">
      <span className="result-card__check"><Check size={28} /></span>
      <div className="result-card__copy">
        <h3>Your file is ready</h3>
        <p>{filename} · {formatBytes(blob.size)}</p>
        {warning && <p className="inline-warning"><TriangleAlert size={15} />{warning}</p>}
        {message && <p role="status">{message}</p>}
        {error && <p className="inline-warning" role="alert"><TriangleAlert size={15} />{error}</p>}
      </div>
      <div className="result-card__actions">
        <button className="button button--primary" type="button" onClick={save}><Download size={18} /> Save</button>
        {canShareFile(blob, filename) && <button className="button button--secondary" type="button" onClick={() => void share()}><Share2 size={18} /> Share</button>}
      </div>
    </section>
  );
}
