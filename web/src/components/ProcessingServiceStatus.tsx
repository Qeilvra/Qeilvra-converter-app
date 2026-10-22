import { useEffect, useState } from 'react';
import { checkProcessingService, processingApiDescription, type ProcessingOperation } from '../lib/processingApi';

export function ProcessingServiceStatus({ operation }: { operation: ProcessingOperation }) {
  const [state, setState] = useState<'checking' | 'ready' | 'unavailable'>('checking');
  const [message, setMessage] = useState('Checking processing service and required dependencies…');
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    let active = true;
    setState('checking');
    void checkProcessingService().then((health) => {
      if (!active) return;
      const available = health.operations.includes(operation);
      setState(available ? 'ready' : 'unavailable');
      setMessage(available ? 'The service reports this operation is available.' : 'The required conversion engine is unavailable. Configure the processing service before retrying.');
    }).catch((reason: unknown) => {
      if (active) { setState('unavailable'); setMessage(reason instanceof Error ? reason.message : 'Service health could not be checked.'); }
    });
    return () => { active = false; };
  }, [operation, attempt]);
  return <div className={`engine-strip engine-strip--${state}`} role="status"><span className="engine-strip__dot" /><div><strong>{state === 'ready' ? 'Processing service available' : state === 'checking' ? 'Checking processing service' : 'Processing service unavailable'}</strong><small>{message}</small><small>{processingApiDescription}</small>{state === 'unavailable' && <button type="button" className="button button--secondary" onClick={() => setAttempt((value) => value + 1)}>Check again</button>}</div></div>;
}
