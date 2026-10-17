import { ArrowRight } from 'lucide-react';
import { Link } from 'react-router-dom';
import type { ToolDefinition } from '../data/tools';

export function ToolCard({ tool, large = false }: { tool: ToolDefinition; large?: boolean }) {
  const { Icon } = tool;
  return (
    <Link to={`/tools/${tool.id}`} className={`tool-card ${large ? 'tool-card--large' : ''}`}>
      <span className={`tool-icon tool-icon--${tool.color}`}><Icon size={large ? 28 : 24} /></span>
      <span className="tool-card__copy"><strong>{tool.shortName}</strong><small>{tool.description}</small></span>
      <ArrowRight className="tool-card__arrow" size={18} />
    </Link>
  );
}
