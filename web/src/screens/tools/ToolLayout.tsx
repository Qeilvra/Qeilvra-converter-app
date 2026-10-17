import type { ReactNode } from 'react';
import type { ToolDefinition } from '../../data/tools';
import { PageHeader } from '../../components/PageHeader';
import { PrivacyStrip } from '../../components/StatusBanner';

export function ToolLayout({ tool, children, note, mode = 'local' }: { tool: ToolDefinition; children: ReactNode; note?: string; mode?: 'local' | 'service' }) {
  const { Icon } = tool;
  return (
    <div className="page page--tool">
      <PageHeader title={tool.name} help={note ?? tool.description} />
      <section className="tool-intro">
        <span className={`tool-icon tool-icon--${tool.color} tool-icon--xl`}><Icon size={34} /></span>
        <div><h2>{tool.name}</h2><p>{note ?? tool.description}</p></div>
      </section>
      {children}
      <PrivacyStrip mode={mode} />
    </div>
  );
}
