// Real browser engines; all remote requests blocked. Run before|after.
import { createRequire } from 'node:module';
import { spawn } from 'node:child_process';
import { mkdir, readFile, readdir, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import assert from 'node:assert/strict';
const require = createRequire(new URL('../web/package.json', import.meta.url));
const { chromium } = require('@playwright/test');
const phase = process.argv[2] ?? 'after'; assert(['before', 'after'].includes(phase));
const root = resolve('.tools/professional-conversion');
const modulePath = phase === 'before' ? '/.pdf-word-benchmark/professional/' : '/src/lib/';
const server = spawn(process.execPath, [resolve('web/node_modules/vite/bin/vite.js'), '--host', '127.0.0.1', '--port', '4182', '--strictPort'], { cwd: resolve('web'), windowsHide: true, stdio: 'pipe' });
let logs = ''; server.stderr.on('data', d => { logs += d; });
let browser;
try {
  let ready = false;
  for (let i = 0; i < 100; i++) {
    if (server.exitCode !== null) throw new Error(logs);
    try { ready = (await fetch('http://127.0.0.1:4182/')).ok; } catch { }
    if (ready) break;
    await new Promise(r => setTimeout(r, 200));
  }
  assert(ready, logs);
  browser = await chromium.launch(); const page = await browser.newPage(); const remote = [];
  await page.route('**/*', route => {
    const url = new URL(route.request().url());
    if (!['127.0.0.1', 'localhost'].includes(url.hostname) || url.pathname.startsWith('/api/')) { remote.push(url.href); return route.abort(); }
    return route.continue();
  });
  await page.goto('http://127.0.0.1:4182/');
  const records = [];
  for (const name of (await readdir(resolve(root, 'sources'))).filter(n => /\.(pdf|docx)$/.test(n) && (!process.argv[3] || n === process.argv[3])).sort()) {
    const isPdf = name.endsWith('.pdf');
    const output = resolve(root, isPdf ? `browser-${phase}` : `word-pdf-${phase}`); await mkdir(output, { recursive: true });
    const bytes = [...await readFile(resolve(root, 'sources', name))];
    const result = await page.evaluate(async ({ name, bytes, modulePath, isPdf }) => {
      const start = performance.now();
      try {
        const engine = await import(modulePath + (isPdf ? 'localDocuments.ts' : 'localWordPdf.ts'));
        const blob = await (isPdf ? engine.pdfToDocxLocal : engine.docxToPdfLocal)(new File([new Uint8Array(bytes)], name));
        return { bytes: [...new Uint8Array(await blob.arrayBuffer())], milliseconds: performance.now() - start };
      } catch (e) { return { error: e.message, milliseconds: performance.now() - start }; }
    }, { name, bytes, modulePath, isPdf });
    if (result.bytes) await writeFile(resolve(output, name.replace(/\.(pdf|docx)$/, isPdf ? '.docx' : '.pdf')), Buffer.from(result.bytes));
    const { bytes: _, ...record } = result; records.push({ name, ...record }); console.log(name, record);
  }
  assert.deepEqual(remote, []);
  let previous = [];
  if (process.argv[3]) { try { previous = JSON.parse(await readFile(resolve(root, `browser-${phase}.json`), 'utf8')).records; } catch { } }
  await writeFile(resolve(root, `browser-${phase}.json`), JSON.stringify({ browser: await browser.version(), remote, records: [...previous.filter(r => !records.some(n => n.name === r.name)), ...records] }, null, 2));
} finally { if (browser) await browser.close(); server.kill(); }
