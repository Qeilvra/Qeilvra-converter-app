import { Search, Sparkles } from 'lucide-react';
import { useMemo, useState } from 'react';
import { ToolCard } from '../components/ToolCard';
import { tools } from '../data/tools';

export function ToolsPage() {
  const [query, setQuery] = useState('');
  const filtered = useMemo(() => tools.filter((tool) => `${tool.name} ${tool.description}`.toLowerCase().includes(query.toLowerCase())), [query]);
  return (
    <div className="page">
      <header className="simple-header"><span className="eyebrow"><Sparkles size={15} /> FileFlow toolkit</span><h1>All file tools</h1><p>Useful browser tools with local processing and honest limitations.</p></header>
      <label className="search-field"><Search /><input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Search tools" aria-label="Search tools" /></label>
      <div className="tool-grid tool-grid--all">
        {filtered.map((tool) => <ToolCard key={tool.id} tool={tool} large />)}
      </div>
      {!filtered.length && <div className="empty-state"><Search /><h2>No matching tools</h2><p>Try a different search term.</p></div>}
    </div>
  );
}
