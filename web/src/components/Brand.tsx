import { ArrowLeftRight } from 'lucide-react';

export function Brand({ compact = false }: { compact?: boolean }) {
  return (
    <div className={`brand ${compact ? 'brand--compact' : ''}`} aria-label="FileFlow">
      <span className="brand__mark" aria-hidden="true"><ArrowLeftRight size={22} strokeWidth={2.5} /></span>
      <span className="brand__copy">
        <span className="brand__name">File<span>Flow</span></span>
        {!compact && <span className="brand__tagline">All your file tools in one place</span>}
      </span>
    </div>
  );
}
