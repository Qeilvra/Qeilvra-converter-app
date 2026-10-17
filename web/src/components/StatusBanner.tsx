import { AlertCircle, LoaderCircle, ShieldCheck } from 'lucide-react';

export function StatusBanner({ state, error }: { state: 'idle' | 'reading' | 'processing' | 'success' | 'error'; error?: string }) {
  if (state === 'idle' || state === 'success') return null;
  if (state === 'error') return <div className="status-banner status-banner--error" role="alert"><AlertCircle /> <span><strong>Couldn’t complete that</strong>{error}</span></div>;
  return <div className="status-banner" aria-live="polite"><LoaderCircle className="spin" /> <span><strong>{state === 'reading' ? 'Reading your file' : 'Processing locally'}</strong>Your file never leaves this device.</span></div>;
}

export function PrivacyStrip() {
  return <div className="privacy-strip"><ShieldCheck /><span><strong>Private processing</strong>Your files stay in this browser and are not uploaded.</span></div>;
}
