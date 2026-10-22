import { Clock3, Grid2X2, Home, LogIn, LogOut, Settings, UserRound } from 'lucide-react';
import { useState } from 'react';
import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { Brand } from './Brand';

const nav = [
  { to: '/', label: 'Home', Icon: Home },
  { to: '/recent', label: 'Recent', Icon: Clock3 },
  { to: '/tools', label: 'Tools', Icon: Grid2X2 },
  { to: '/settings', label: 'Settings', Icon: Settings }
];

export function AppShell() {
  const { user, signOut } = useAuth();
  const navigate = useNavigate();
  const [signingOut, setSigningOut] = useState(false);
  const logout = async () => {
    setSigningOut(true);
    try {
      await signOut();
      navigate('/login');
    } finally {
      setSigningOut(false);
    }
  };

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
        <div className="sidebar-account">
          {user ? <>
            <span className="sidebar-account__avatar"><UserRound /></span>
            <span className="sidebar-account__copy"><strong>Signed in</strong><small title={user.email}>{user.email}</small></span>
            <button type="button" aria-label="Log out" title="Log out" onClick={logout} disabled={signingOut}><LogOut /></button>
          </> : <NavLink to="/login"><LogIn /><span>Log in</span></NavLink>}
        </div>
        <div className="privacy-note">
          <span className="privacy-note__dot" />
          <strong>Private by design</strong>
          <p>Process files on your device. No account required.</p>
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
