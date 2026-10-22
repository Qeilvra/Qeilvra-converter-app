// @vitest-environment jsdom
import '@testing-library/jest-dom/vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { Session } from '@supabase/supabase-js';

const mocks = vi.hoisted(() => ({
  signInWithPassword: vi.fn(),
  signOut: vi.fn(),
  authListener: undefined as ((event: string, session: Session | null) => void) | undefined
}));

vi.mock('../lib/supabase', () => ({
  isSupabaseConfigured: true,
  supabase: {
    auth: {
      onAuthStateChange: vi.fn((listener) => {
        mocks.authListener = listener;
        queueMicrotask(() => listener('INITIAL_SESSION', null));
        return { data: { subscription: { unsubscribe: vi.fn() } } };
      }),
      signInWithPassword: mocks.signInWithPassword,
      signOut: mocks.signOut
    }
  }
}));

import { AuthProvider, useAuth } from './AuthContext';

function Harness() {
  const { loading, user, signIn, signOut: logout } = useAuth();
  return <div>
    <span>{loading ? 'Loading' : user?.email ?? 'Guest'}</span>
    <button onClick={() => signIn('person@example.com', 'secret12')}>Sign in</button>
    <button onClick={() => logout()}>Log out</button>
  </div>;
}

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
  mocks.authListener = undefined;
});

describe('Supabase authentication', () => {
  it('signs in with email and password and follows session changes', async () => {
    mocks.signInWithPassword.mockResolvedValue({ error: null });
    render(<AuthProvider><Harness /></AuthProvider>);
    await screen.findByText('Guest');
    fireEvent.click(screen.getByRole('button', { name: 'Sign in' }));
    await waitFor(() => expect(mocks.signInWithPassword).toHaveBeenCalledWith({ email: 'person@example.com', password: 'secret12' }));

    mocks.authListener?.('SIGNED_IN', { user: { email: 'person@example.com' } } as Session);
    expect(await screen.findByText('person@example.com')).toBeInTheDocument();
  });

  it('logs out only the current browser session', async () => {
    mocks.signOut.mockResolvedValue({ error: null });
    render(<AuthProvider><Harness /></AuthProvider>);
    await screen.findByText('Guest');
    fireEvent.click(screen.getByRole('button', { name: 'Log out' }));
    await waitFor(() => expect(mocks.signOut).toHaveBeenCalledWith({ scope: 'local' }));
  });
});
