import { Clock3, Grid2X2, Home, Settings } from 'lucide-react';
import { NavLink, Outlet } from 'react-router-dom';
import { Brand } from './Brand';

const nav = [
  { to: '/', label: 'Home', Icon: Home },
  { to: '/recent', label: 'Recent', Icon: Clock3 },
  { to: '/tools', label: 'Tools', Icon: Grid2X2 },
  { to: '/settings', label: 'Settings', Icon: Settings }
];

export function AppShell() {
  return (
    <div className="app-shell">
      <aside className="desktop-sidebar">
        <Brand />
        <nav aria-label="Main navigation">
          {nav.map(({ to, label, Icon }) => (
            <NavLink key={to} to={to} end={to === '/'}>
              <Icon size={21} /> <span>{label}</span>
            </NavLink>
          ))}
        </nav>
        <div className="privacy-note">
          <span className="privacy-note__dot" />
          <strong>Private by design</strong>
          <p>Local when practical; secure service when fidelity requires it.</p>
        </div>
      </aside>
      <main className="app-main"><Outlet /></main>
      <nav className="bottom-nav" aria-label="Main navigation">
        {nav.map(({ to, label, Icon }) => (
          <NavLink key={to} to={to} end={to === '/'}>
            <Icon size={22} /><span>{label}</span>
          </NavLink>
        ))}
      </nav>
    </div>
  );
}
