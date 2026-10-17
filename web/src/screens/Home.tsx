import { ArrowRight, FileImage, FileSpreadsheet, FileText, LockKeyhole, Settings } from 'lucide-react';
import { Link, useNavigate } from 'react-router-dom';
import { Brand } from '../components/Brand';
import { ToolCard } from '../components/ToolCard';
import { popularToolIds, tools } from '../data/tools';

export function Home() {
  const navigate = useNavigate();
  const popular = popularToolIds.map((id) => tools.find((tool) => tool.id === id)!);
  return (
    <div className="page home-page">
      <header className="mobile-home-header">
        <Brand />
        <Link className="icon-button" to="/settings" aria-label="Settings"><Settings /></Link>
      </header>

      <section className="hero-card">
        <div className="hero-card__copy">
          <span className="eyebrow"><LockKeyhole size={15} /> Files stay on your device</span>
          <h1>Convert, compress,<br />edit &amp; share.</h1>
          <p>Fast. Simple. Private.</p>
          <button className="button button--primary button--hero" type="button" onClick={() => navigate('/tools')}>
            Choose a tool <ArrowRight size={20} />
          </button>
        </div>
        <div className="hero-files" aria-hidden="true">
          <span className="hero-file hero-file--pdf"><FileText /></span>
          <span className="hero-file hero-file--word"><FileImage /></span>
          <span className="hero-file hero-file--excel"><FileSpreadsheet /></span>
          <div className="hero-orbit" />
        </div>
      </section>

      <section className="section-block">
        <div className="section-heading"><div><span className="eyebrow">Get things done</span><h2>Popular tools</h2></div><Link to="/tools">See all <ArrowRight size={16} /></Link></div>
        <div className="tool-grid tool-grid--popular">
          {popular.map((tool) => <ToolCard key={tool.id} tool={tool} />)}
        </div>
      </section>

      <Link to="/tools/prepare-upload" className="feature-card">
        <div className="feature-card__art" aria-hidden="true"><FileText /><FileImage /></div>
        <div><span className="eyebrow">Built for applications</span><h2>Prepare for upload</h2><p>Resize photos, signatures and documents for forms, jobs and admissions.</p></div>
        <span className="feature-card__arrow"><ArrowRight /></span>
      </Link>
    </div>
  );
}
