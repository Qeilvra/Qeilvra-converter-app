import type { ReactNode } from 'react';
import type { ToolDefinition } from '../../data/tools';
import { PageHeader } from '../../components/PageHeader';
import { PrivacyStrip } from '../../components/StatusBanner';

export function ToolLayout({ tool, children, note, mode = 'local', busy = false }: { tool: ToolDefinition; children: ReactNode; note?: string; mode?: 'local' | 'service'; busy?: boolean }) {
  const { Icon } = tool;
  return (
    <div className="page page--tool">
      <PageHeader title={tool.name} help={note ?? tool.description} />
      <section className="tool-intro">
        <span className={`tool-icon tool-icon--${tool.color} tool-icon--xl`}><Icon size={34} /></span>
        <div><h2>{tool.name}</h2><p>{note ?? tool.description}</p></div>
      </section>
      <fieldset className="tool-workflow" disabled={busy} aria-busy={busy}>{children}</fieldset>
      <PrivacyStrip mode={mode} />
    </div>
  );
}
