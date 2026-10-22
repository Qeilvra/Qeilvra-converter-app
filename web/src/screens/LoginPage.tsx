import { AlertCircle, ArrowRight, LoaderCircle, LockKeyhole, Mail } from 'lucide-react';
import { useEffect, useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { Brand } from '../components/Brand';

export function LoginPage() {
  const { configured, loading, session, signIn } = useAuth();
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!loading && session) navigate('/', { replace: true });
  }, [loading, navigate, session]);

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setError('');
    setSubmitting(true);
    try {
      await signIn(email.trim(), password);
      navigate('/', { replace: true });
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'Unable to sign in. Please try again.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="auth-page">
      <section className="auth-card" aria-labelledby="login-title">
        <Brand />
        <div className="auth-card__heading">
          <span className="auth-card__icon"><LockKeyhole /></span>
          <div><span className="eyebrow">Secure account</span><h1 id="login-title">Welcome back</h1><p>Sign in to your FileFlow account.</p></div>
        </div>

        {!configured && (
          <div className="auth-message auth-message--warning" role="alert">
            <AlertCircle /><span><strong>Authentication needs configuration</strong>Add the Supabase project URL and publishable key to <code>web/.env.local</code>.</span>
          </div>
        )}

        {error && <div className="auth-message auth-message--error" role="alert"><AlertCircle /><span>{error}</span></div>}

        <form className="auth-form" onSubmit={submit}>
          <label>
            <span>Email address</span>
            <span className="auth-input"><Mail /><input type="email" name="email" autoComplete="email" inputMode="email" required value={email} onChange={(event) => setEmail(event.target.value)} placeholder="you@example.com" /></span>
          </label>
          <label>
            <span>Password</span>
            <span className="auth-input"><LockKeyhole /><input type="password" name="password" autoComplete="current-password" required minLength={6} value={password} onChange={(event) => setPassword(event.target.value)} placeholder="Your password" /></span>
          </label>
          <button className="button button--primary button--full" type="submit" disabled={!configured || loading || submitting}>
            {submitting ? <LoaderCircle className="spin" /> : <ArrowRight />} {submitting ? 'Signing in…' : 'Sign in'}
          </button>
        </form>
        <p className="auth-card__privacy">Authentication is handled by Supabase. FileFlow does not store your password.</p>
      </section>
    </div>
  );
}
