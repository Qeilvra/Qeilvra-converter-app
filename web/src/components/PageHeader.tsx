import { ArrowLeft, CircleHelp } from 'lucide-react';
import { useNavigate } from 'react-router-dom';

export function PageHeader({ title, subtitle, help }: { title: string; subtitle?: string; help?: string }) {
  const navigate = useNavigate();
  return (
    <header className="page-header">
      <button className="icon-button" type="button" onClick={() => navigate(-1)} aria-label="Go back"><ArrowLeft /></button>
      <div>
        <h1>{title}</h1>
        {subtitle && <p>{subtitle}</p>}
      </div>
      <button className="icon-button" type="button" aria-label={help ?? 'Help'} title={help ?? 'Help'}><CircleHelp /></button>
    </header>
  );
}
