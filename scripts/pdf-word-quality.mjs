// Real local-browser conversions, before/after archives, no processing backend.
// Usage: node scripts/pdf-word-quality.mjs before|after [path/to/primary-cv.pdf]
import { createRequire } from 'node:module';
import { spawn, spawnSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { mkdir, readdir, readFile, writeFile, copyFile } from 'node:fs/promises';
import { resolve, basename } from 'node:path';
import assert from 'node:assert/strict';
const require = createRequire(new URL('../web/package.json', import.meta.url));
const { chromium } = require('@playwright/test');
const phase = process.argv[2] ?? 'after';
assert(['before', 'after'].includes(phase));
let modulePath = '/src/lib/localDocuments.ts'; let engineRevision = 'working-tree';
if (phase === 'before') {
  const ref = process.env.FILEFLOW_BASELINE_REF || 'HEAD';
  const legacy = spawnSync('git', ['show', `${ref}:web/src/lib/localDocuments.ts`], { encoding: 'utf8', windowsHide: true });
  assert.equal(legacy.status, 0, legacy.stderr);
  engineRevision = spawnSync('git', ['rev-parse', ref], { encoding: 'utf8', windowsHide: true }).stdout.trim();
  const baseline = resolve('web/.pdf-word-benchmark'); await mkdir(baseline, { recursive: true });
  await writeFile(resolve(baseline, 'localDocuments.ts'), legacy.stdout);
  modulePath = '/.pdf-word-benchmark/localDocuments.ts';
}
const root = resolve('.tools/pdf-word-quality');
const sources = resolve(root, 'sources');
await mkdir(sources, { recursive: true });
if (process.argv[3]) await copyFile(resolve(process.argv[3]), resolve(sources, basename(process.argv[3])));
const output = resolve(root, phase); await mkdir(output, { recursive: true });
const server = spawn(process.execPath, [resolve('web/node_modules/vite/bin/vite.js'), '--host', '127.0.0.1', '--port', '4181', '--strictPort'], { cwd: resolve('web'), windowsHide: true, stdio: 'pipe' });
let logs = ''; server.stderr.on('data', data => { logs += data; });
let browser;
try {
  let ready = false;
  for (let attempt = 0; attempt < 100; attempt++) {
    if (server.exitCode !== null) throw new Error(logs);
    try { ready = (await fetch('http://127.0.0.1:4181/')).ok; } catch { }
    if (ready) break;
    await new Promise(resolve => setTimeout(resolve, 200));
  }
  assert(ready, 'Local Vite server failed to start');
  browser = await chromium.launch();
  const page = await browser.newPage();
  const remoteRequests = [];
  await page.route('**/*', route => {
    const url = new URL(route.request().url());
    if (!['127.0.0.1', 'localhost'].includes(url.hostname)) { remoteRequests.push(url.href); return route.abort(); }
    if (url.pathname.startsWith('/api/')) { remoteRequests.push(url.href); return route.abort(); }
    return route.continue();
  });
  await page.goto('http://127.0.0.1:4181/');
  const results = [];
  for (const name of (await readdir(sources)).filter(name => name.endsWith('.pdf')).sort()) {
    const bytes = [...await readFile(resolve(sources, name))];
    const result = await page.evaluate(async ({ bytes, name, modulePath }) => {
      const engine = await import(modulePath);
      const start = performance.now(); let ticks = 0; let longestStallMs = 0; let last = performance.now();
      const timer = setInterval(() => { const now = performance.now(); longestStallMs = Math.max(longestStallMs, now - last - 16); last = now; ticks++; }, 16);
      try {
        const blob = await engine.pdfToDocxLocal(new File([new Uint8Array(bytes)], name, { type: 'application/pdf' }));
        return { bytes: [...new Uint8Array(await blob.arrayBuffer())], elapsedMs: performance.now() - start, longestStallMs, ticks };
      } finally { clearInterval(timer); }
    }, { bytes, name, modulePath });
    await writeFile(resolve(output, name.replace(/\.pdf$/, '.docx')), Buffer.from(result.bytes));
    const { bytes: _, ...metrics } = result;
    results.push({ name, sourceSha256: createHash('sha256').update(Buffer.from(bytes)).digest('hex'), outputBytes: result.bytes.length, ...metrics });
    console.log(`${phase}: ${name}: ${Math.round(result.elapsedMs)} ms`);
  }
  assert.deepEqual(remoteRequests, [], 'Conversion attempted a backend or external service');
  await writeFile(resolve(output, 'browser.json'), JSON.stringify({ phase, engineRevision, browser: await browser.version(), remoteRequests, results }, null, 2));
} finally { if (browser) await browser.close(); server.kill(); }
