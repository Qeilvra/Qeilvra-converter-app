import { FileQuestion } from 'lucide-react';
import { Link } from 'react-router-dom';

export function NotFound() {
  return <div className="page"><div className="empty-state"><FileQuestion /><h1>Page not found</h1><p>The page you requested does not exist.</p><Link className="button button--primary" to="/">Back home</Link></div></div>;
}
