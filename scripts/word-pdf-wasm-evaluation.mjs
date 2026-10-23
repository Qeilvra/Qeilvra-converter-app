// Local evaluation of the official ZetaJS conversion example. No document uploads.
import { createRequire } from 'node:module';
import { createServer } from 'node:http';
import { readFile, writeFile, mkdir, stat } from 'node:fs/promises';
import { resolve, extname, basename } from 'node:path';
import assert from 'node:assert/strict';
const require = createRequire(new URL('../web/package.json', import.meta.url));
const { chromium } = require('@playwright/test');
const root = resolve('.tools/word-pdf-rendering');
const runtime = resolve(root, 'research');
const withFonts = process.argv.includes('--system-fonts');
const fontNames = ['arial.ttf','arialbd.ttf','ariali.ttf','arialbi.ttf','GARA.TTF','GARABD.TTF','GARAIT.TTF','times.ttf','timesbd.ttf','timesi.ttf','timesbi.ttf','calibri.ttf','calibrib.ttf','calibrii.ttf','calibriz.ttf'];
const server = createServer(async (request, response) => {
  try {
    const name = basename(new URL(request.url, 'http://localhost').pathname) || 'index.html';
    const file = withFonts && fontNames.includes(name) ? resolve('C:/Windows/Fonts', name) : resolve(runtime, name);
    let data = await readFile(file);
    if (withFonts && name === 'index.html') {
      data = Buffer.from(data.toString().replace('zHM.start(() => {', `const fonts = await Promise.all(${JSON.stringify(fontNames)}.map(async name => ({name, bytes:new Uint8Array(await (await fetch('/'+name)).arrayBuffer())})));
      zHM.Module.preRun = [() => { FS.mkdirTree('/instdir/share/fonts/truetype'); for(const f of fonts) FS.writeFile('/instdir/share/fonts/truetype/'+f.name, f.bytes); }];
      zHM.start(() => {`));
    }
    response.writeHead(200, { 'Content-Type': ({'.html':'text/html','.js':'text/javascript','.wasm':'application/wasm'})[extname(file)] || 'application/octet-stream', 'Cross-Origin-Opener-Policy':'same-origin', 'Cross-Origin-Embedder-Policy':'require-corp', 'Cache-Control':'no-store' });
    response.end(data);
  } catch { response.writeHead(404).end(); }
});
await new Promise(r => server.listen(4185, '127.0.0.1', r));
let browser;
try {
  browser = await chromium.launch({ args: ['--enable-precise-memory-info'] });
  const page = await browser.newPage();
  const external = [], errors = [];
  await page.route('**/*', route => {
    const url = new URL(route.request().url());
    if (url.hostname !== '127.0.0.1') { external.push(url.href); return route.abort(); }
    return route.continue();
  });
  page.on('console', m => { if (m.type() === 'error') errors.push(m.text()); console.log(m.type(), m.text().slice(0,300)); });
  page.on('pageerror', e => { errors.push(e.message); console.log('PAGE ERROR', e.message); });
  const start = performance.now();
  await page.goto('http://127.0.0.1:4185/');
  await page.locator('#input').waitFor();
  await page.waitForFunction(() => !document.getElementById('input').disabled, { timeout: 180_000 });
  console.log('engine ready', performance.now()-start);
  // Capture blob bytes at creation, before the official demo revokes the URL.
  await page.evaluate(() => { window.pdfOutputs=[]; const create=URL.createObjectURL.bind(URL); URL.createObjectURL=blob=>{if(blob.type==='application/pdf')blob.arrayBuffer().then(b=>window.pdfOutputs.push([...new Uint8Array(b)])); return create(blob)}; });
  const output = resolve(root, withFonts ? 'wasm-fonts' : 'wasm-evaluation'); await mkdir(output, { recursive:true });
  const records = [];
  for (const name of ['professional-cv','columns','complex-report','urdu-rtl','shapes']) {
    const start = performance.now();
    await page.locator('#input').setInputFiles(resolve(root, 'sources', name+'.docx'));
    await page.waitForFunction(() => window.pdfOutputs.length > 0, { timeout:120_000 });
    const data = await page.evaluate(() => window.pdfOutputs.shift());
    const bytes = Buffer.from(data); assert.equal(bytes.subarray(0,5).toString(), '%PDF-');
    await writeFile(resolve(output,name+'.pdf'), bytes);
    records.push({ name, milliseconds: performance.now()-start, bytes:bytes.length, memory:await page.evaluate(()=>performance.memory ? {usedJSHeapSize:performance.memory.usedJSHeapSize,totalJSHeapSize:performance.memory.totalJSHeapSize} : null) });
    console.log(records.at(-1));
  }
  assert.deepEqual(external, []);
  await writeFile(resolve(output,'results.json'), JSON.stringify({ records, errors, externalRequests:external, startupMilliseconds:performance.now()-start },null,2));
} finally { if(browser)await browser.close(); await new Promise(r=>server.close(r)); }
