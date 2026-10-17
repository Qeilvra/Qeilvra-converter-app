import { AlertCircle, LoaderCircle, ShieldCheck } from 'lucide-react';

type ProcessingMode = 'local' | 'service';

export function StatusBanner({ state, error, mode = 'local' }: { state: 'idle' | 'reading' | 'processing' | 'success' | 'error'; error?: string; mode?: ProcessingMode }) {
  if (state === 'idle' || state === 'success') return null;
  if (state === 'error') return <div className="status-banner status-banner--error" role="alert"><AlertCircle /> <span><strong>Couldn’t complete that</strong>{error}</span></div>;
  return <div className="status-banner" aria-live="polite"><LoaderCircle className="spin" /> <span><strong>{state === 'reading' ? 'Reading your file' : mode === 'service' ? 'Processing securely' : 'Processing locally'}</strong>{mode === 'service' ? 'The configured FileFlow service is processing a temporary copy.' : 'Your file stays on this device.'}</span></div>;
}

export function PrivacyStrip({ mode = 'local' }: { mode?: ProcessingMode }) {
  return <div className="privacy-strip"><ShieldCheck /><span><strong>{mode === 'service' ? 'Temporary service processing' : 'Private local processing'}</strong>{mode === 'service' ? 'This operation uses the configured FileFlow worker, which deletes temporary files after the request. Production deployments require HTTPS.' : 'This operation stays in the browser and is not uploaded.'}</span></div>;
}
