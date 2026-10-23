// Genuine LibreOffice layout in an isolated, short-lived browser frame. No service fallback.
export async function renderDocxOffline(file: File, fonts: File[] = [], progress?: (message: string) => void, signal?: AbortSignal): Promise<Blob> {
  if (!file.size || file.size > 20 * 1024 * 1024) throw new Error('Office rendering supports DOCX files up to 20 MB.');
  if (!globalThis.crossOriginIsolated || typeof SharedArrayBuffer === 'undefined') throw new Error('Offline office rendering is unavailable in this browser context. Open FileFlow in a supported browser with cross-origin isolation enabled. No text-only PDF was created.');
  const deviceMemory = (navigator as Navigator & {deviceMemory?: number}).deviceMemory;
  if (deviceMemory && deviceMemory < 4) throw new Error('This office engine reserves 1 GiB of WebAssembly memory and is disabled on low-memory devices. Export from an installed office app instead. No text-only PDF was created.');
  if (fonts.length > 32 || fonts.some(f => f.size > 10 * 1024 * 1024 || !/\.(ttf|otf)$/i.test(f.name)) || fonts.reduce((sum,f) => sum + f.size, 0) > 40 * 1024 * 1024) throw new Error('Select up to 32 TTF/OTF fonts, at most 10 MB each and 40 MB combined.');
  signal?.throwIfAborted();
  progress?.('Checking the offline office engine…');
  const manifestSignal = signal ? AbortSignal.any([signal, AbortSignal.timeout(15_000)]) : AbortSignal.timeout(15_000);
  const manifest = await fetch('/office-runtime/manifest.json', { signal:manifestSignal, credentials: 'omit' });
  const config: unknown = manifest.ok && manifest.headers.get('content-type')?.includes('application/json') ? await manifest.json() : null;
  if (!config || typeof config !== 'object' || !('engine' in config) || config.engine !== 'LibreOffice WASM') throw new Error('The offline office engine is not packaged in this FileFlow build. Use an installed office app to export PDF. The developer can install the local runtime using the documented setup command. No text-only PDF was created.');
  const source = await file.arrayBuffer();
  const fontData = await Promise.all(fonts.map(async (font, i) => ({ name: `font-${i}.${font.name.toLowerCase().endsWith('.otf') ? 'otf' : 'ttf'}`, bytes: await font.arrayBuffer() })));
  signal?.throwIfAborted();
  return new Promise<Blob>((resolve, reject) => {
    const frame = document.createElement('iframe');
    frame.title = 'Offline document renderer'; frame.setAttribute('aria-hidden', 'true'); frame.tabIndex = -1;
    frame.style.cssText = 'position:fixed;left:-10000px;top:0;width:1024px;height:768px;border:0;visibility:hidden';
    frame.allow = 'cross-origin-isolated';
    const id = crypto.randomUUID(); let initialized = false; let done = false;
    const cleanup = () => {
      window.clearTimeout(timeout); window.removeEventListener('message', receive); signal?.removeEventListener('abort', abort);
      // A posted disposal message can be lost when its frame is immediately removed.
      try { (frame.contentWindow as (Window & {fileflowDispose?: () => void}) | null)?.fileflowDispose?.(); } catch { /* Frame may have failed before loading. */ }
      frame.remove();
    };
    const fail = (error: Error) => { if (done) return; done = true; cleanup(); reject(error); };
    const abort = () => fail(new DOMException('Office rendering cancelled.', 'AbortError'));
    const timeout = window.setTimeout(() => fail(new Error('The offline office renderer exceeded three minutes. Try a smaller document or export from an installed office app. No text-only fallback was used.')), 180_000);
    const receive = (event: MessageEvent) => {
      if (event.origin !== location.origin || event.source !== frame.contentWindow || !event.data || done) return;
      if (event.data.type === 'bootstrap' && !initialized) {
        initialized = true;
        progress?.('Loading LibreOffice on this device…');
        frame.contentWindow!.postMessage({ type: 'render', id, source, fonts: fontData }, location.origin, [source, ...fontData.map(f => f.bytes)]);
      } else if (event.data.id === id) {
        if (event.data.type === 'laying-out') progress?.('Rendering the original Word document layout…');
        else if (event.data.type === 'error') fail(new Error(`Office rendering failed: ${String(event.data.message).slice(0, 500)} No text-only fallback was used.`));
        else if (event.data.type === 'pdf') {
          const bytes: unknown = event.data.bytes;
          if (!(bytes instanceof ArrayBuffer) || bytes.byteLength < 5 || bytes.byteLength > 100 * 1024 * 1024 || new TextDecoder().decode(new Uint8Array(bytes,0,5)) !== '%PDF-') { fail(new Error('The office renderer did not return a valid PDF.')); return; }
          // Parse independently before presenting Save, rather than trusting a header alone.
          void import('pdf-lib').then(async ({ PDFDocument }) => {
            const pdf = await PDFDocument.load(bytes);
            if (!pdf.getPageCount() || pdf.getPageCount() > 100) throw new Error('The office output exceeds the supported 1–100 page range.');
            if (done) return;
            done = true; cleanup(); resolve(new Blob([bytes], { type: 'application/pdf' }));
          }).catch(error => fail(error instanceof Error ? error : new Error('Could not open the rendered PDF.')));
        }
      }
    };
    window.addEventListener('message', receive); signal?.addEventListener('abort', abort, { once:true });
    frame.src = '/office-renderer/index.html'; document.body.appendChild(frame);
  });
}
