import { Clock3, Download, FileCheck2, Trash2 } from 'lucide-react';
import { useEffect, useState } from 'react';
import { clearHistory, downloadBlob, formatBytes, readHistory, readStoredOutput } from '../lib/files';
import type { RecentItem } from '../types';

export function Recent() {
  const [items, setItems] = useState<RecentItem[]>(readHistory);
  const [error, setError] = useState('');
  useEffect(() => {
    const update = () => setItems(readHistory());
    window.addEventListener('fileflow-history', update);
    return () => window.removeEventListener('fileflow-history', update);
  }, []);
  return (
    <div className="page">
      <header className="simple-header simple-header--row"><div><span className="eyebrow"><Clock3 size={15} /> On this device</span><h1>Recent activity</h1><p>History stores metadata. Output copies are retained only when you enable that privacy setting.</p></div>{items.length > 0 && <button className="button button--danger-quiet" type="button" onClick={() => clearHistory()}><Trash2 size={17} /> Clear</button>}</header>
      {error && <p className="inline-warning" role="alert">{error}</p>}
      {items.length ? <div className="recent-list">{items.map((item) => (
        <article className="recent-item" key={item.id}>
          <span className="recent-item__icon"><FileCheck2 /></span>
          <div><h2>{item.outputName}</h2><p>{item.operation} · from {item.sourceName}</p><small>{new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(item.createdAt))}</small></div>
          <div className="recent-item__result"><strong>{formatBytes(item.outputSize)}</strong>{item.outputRetained && <button className="button button--secondary" type="button" onClick={() => void (async () => { const output = await readStoredOutput(item.id); if (!output) { setError('This retained output is no longer available. It may have been removed by browser storage cleanup.'); return; } setError(''); downloadBlob(output.blob, output.filename); })()}><Download size={15} /> Save again</button>}</div>
        </article>
      ))}</div> : <div className="empty-state"><Clock3 /><h2>No recent activity</h2><p>Completed tools will appear here. Output retention is off by default.</p></div>}
    </div>
  );
}
