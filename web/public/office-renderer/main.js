// A fresh frame owns each engine instance, its document and all in-memory files.
let helper, requestId, completed = false;
// Emscripten also starts helpers outside its PThread pool (for example audio).
// Keep ownership of every Worker created in this frame so none survives cleanup.
const ownedWorkers = new Set();
const NativeWorker = globalThis.Worker;
globalThis.Worker = class extends NativeWorker {
  constructor(...args) { super(...args); ownedWorkers.add(this); }
  terminate() { ownedWorkers.delete(this); super.terminate(); }
};
const send = (type, extra = {}, transfer = []) => parent.postMessage({ type, id:requestId, ...extra }, location.origin, transfer);
function dispose() {
  // This Emscripten build exposes PThread as a global, not Module.PThread.
  // Terminate explicitly: detaching a frame alone left two workers alive in Chromium.
  try { globalThis.PThread?.terminateAllThreads(); } catch { /* The frame is also removed. */ }
  for (const worker of ownedWorkers) { try { worker.terminate(); } catch { /* Already stopped. */ } }
  ownedWorkers.clear();
}
globalThis.fileflowDispose = dispose;
addEventListener('pagehide', dispose);
addEventListener('error', event => { if (requestId && !completed) send('error', { message:event.message || 'Office engine initialization failed.' }); });
addEventListener('unhandledrejection', event => { if (requestId && !completed) send('error', { message:String(event.reason?.message || event.reason) }); });
addEventListener('message', async event => {
  if (event.source !== parent || event.origin !== location.origin) return;
  if (event.data?.type === 'dispose' && event.data.id === requestId) { dispose(); return; }
  if (event.data?.type !== 'render' || requestId) return;
  requestId = event.data.id;
  try {
    const source = event.data.source, fonts = event.data.fonts;
    if (!(source instanceof ArrayBuffer) || source.byteLength > 20*1024*1024 || !Array.isArray(fonts) || fonts.length > 32) throw Error('Invalid document input.');
    // This URL is deliberately same-origin. No vendor CDN is contacted by the app.
    const { ZetaHelperMain } = await import('/office-runtime/zetaHelper.js');
    helper = new ZetaHelperMain('/office-renderer/office-thread.js', { threadJsType:'module', wasmPkg:`url:${location.origin}/office-runtime/`, blockPageScroll:false });
    helper.Module.preRun = [() => {
      FS.mkdirTree('/instdir/share/fonts/truetype');
      for (const [index,font] of fonts.entries()) {
        if (!(font.bytes instanceof ArrayBuffer) || font.bytes.byteLength > 10*1024*1024) throw Error('Invalid font input.');
        FS.writeFile(`/instdir/share/fonts/truetype/fileflow-${index}.${font.name.endsWith('.otf') ? 'otf' : 'ttf'}`,new Uint8Array(font.bytes));
      }
    }];
    helper.Module.onAbort = reason => send('error', { message:String(reason || 'Office engine aborted, possibly due to insufficient memory.') });
    helper.start(() => {
      helper.thrPort.onmessage = event => {
        if (event.data.cmd === 'ready') {
          FS.writeFile('/tmp/fileflow-input.docx',new Uint8Array(source));
          send('laying-out'); helper.thrPort.postMessage({ cmd:'convert' });
        } else if (event.data.cmd === 'converted') {
          const bytes = FS.readFile('/tmp/fileflow-output.pdf').slice();
          FS.unlink('/tmp/fileflow-input.docx'); FS.unlink('/tmp/fileflow-output.pdf');
          completed = true; send('pdf', { bytes:bytes.buffer }, [bytes.buffer]);
        } else if (event.data.cmd === 'failed') send('error',{ message:event.data.message });
      };
    });
    // ZetaHelper's loader has no script-error callback.
    for (const script of document.scripts) if (script.src.endsWith('/soffice.js')) script.onerror = () => send('error',{message:'The packaged office runtime could not be loaded.'});
  } catch (error) { send('error', { message:String(error?.message || error) }); }
});
send('bootstrap');
