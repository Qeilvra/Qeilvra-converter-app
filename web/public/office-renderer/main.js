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
async function downloadEngine(runtime) {
  const files=['soffice.wasm','soffice.data'].map(name=>runtime.files.find(f=>f.name===name));
  if(files.some(f=>!f || f.encoding!=='gzip' || !/^[a-z.]+\.gzip$/.test(f.path)))throw Error('Invalid office package metadata.');
  const total=files.reduce((sum,f)=>sum+f.downloadBytes,0);let received=0,last=0;
  const digest=async bytes=>Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256',bytes))).map(v=>v.toString(16).padStart(2,'0')).join('');
  return Promise.all(files.map(async file=>{
    const response=await fetch(runtime.assetBase+file.path,{credentials:'omit'});
    if(!response.ok || response.headers.get('content-type')?.includes('text/html'))throw Error(`The office package could not be downloaded (${file.path}, HTTP ${response.status}). Check your connection and retry.`);
    const reader=response.body.getReader(), chunks=[];let size=0;
    while(true){
      const {value,done}=await reader.read();if(done)break;
      size+=value.length;received+=value.length;
      if(size>file.downloadBytes){await reader.cancel();throw Error('The office package has an unexpected size. Reload FileFlow and retry.')}
      chunks.push(value);
      if(performance.now()-last>150){send('progress',{message:`Loading office engine: ${(received/1048576).toFixed(1)} of ${(total/1048576).toFixed(1)} MiB. Your document stays on this device.`});last=performance.now()}
    }
    const encoded=new Uint8Array(size);let offset=0;for(const chunk of chunks){encoded.set(chunk,offset);offset+=chunk.length}
    if(size!==file.downloadBytes || await digest(encoded)!==file.downloadSha256)throw Error('The downloaded office package is incomplete or corrupted. Retry the download.');
    const bytes=await new Response(new Blob([encoded]).stream().pipeThrough(new DecompressionStream('gzip'))).arrayBuffer();
    if(bytes.byteLength!==file.bytes || await digest(bytes)!==file.sha256)throw Error('The office engine failed its integrity check. No document was processed.');
    return bytes;
  }));
}
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
    const source = event.data.source, fonts = event.data.fonts, runtime=event.data.runtime;
    if (!(source instanceof ArrayBuffer) || source.byteLength > 20*1024*1024 || !Array.isArray(fonts) || fonts.length > 32) throw Error('Invalid document input.');
    if(runtime?.format!=='fileflow-office-gzip-v2' || !/^\/office-runtime\/v-[a-f0-9]{16}\/$/.test(runtime.assetBase) || !Array.isArray(runtime.files))throw Error('Invalid office runtime manifest.');
    if(typeof DecompressionStream==='undefined')throw Error('This browser cannot load the compressed office engine. Use a current Chromium or Firefox browser.');
    // Decode compressed static assets locally. No Content-Encoding trick, raw
    // oversized WASM asset, conversion backend, or vendor CDN is required.
    let [wasmBytes,dataBytes]=await downloadEngine(runtime);
    send('progress',{message:'Starting the local office engine…'});
    const { ZetaHelperMain } = await import(runtime.assetBase+'zetaHelper.js');
    helper = new ZetaHelperMain(runtime.assetBase+'office-thread.js', { threadJsType:'module', wasmPkg:`url:${location.origin}${runtime.assetBase}`, blockPageScroll:false });
    helper.Module.wasmBinary=new Uint8Array(wasmBytes);
    helper.Module.getPreloadedPackage=()=>dataBytes;
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
          wasmBytes=null;dataBytes=null;delete helper.Module.wasmBinary;
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
