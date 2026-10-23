// End-to-end opt-in through the actual React button and a real local LibreOffice worker.
// Start server on loopback :8091 using the documented workspace-local LibreOffice path.
import { createRequire } from 'node:module';
import { spawn } from 'node:child_process';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import assert from 'node:assert/strict';
const require = createRequire(new URL('../web/package.json', import.meta.url));
const { chromium } = require('@playwright/test');
const root = resolve('.tools/professional-conversion'); const output = resolve(root, 'office-button'); await mkdir(output, { recursive: true });
const service = 'http://127.0.0.1:8091/api/v1';
const health = await (await fetch(service + '/health/capabilities', { signal: AbortSignal.timeout(40_000) })).json();
assert(health.operations?.includes('docx-to-pdf'), JSON.stringify(health));
const server = spawn(process.execPath, [resolve('web/node_modules/vite/bin/vite.js'), '--host', '127.0.0.1', '--port', '4183', '--strictPort'], { cwd: resolve('web'), env: { ...process.env, VITE_PROCESSING_API_URL: service }, windowsHide: true, stdio: 'pipe' });
let logs = ''; server.stderr.on('data', d => { logs += d; }); let browser;
try {
  let ready = false;
  for (let i = 0; i < 100; i++) { try { ready = (await fetch('http://127.0.0.1:4183/')).ok; } catch { } if (ready) break; await new Promise(r => setTimeout(r, 200)); }
  assert(ready, logs); browser = await chromium.launch();
  const records = [];
  for (const name of ['office-report', 'supplied-cv']) {
    const page = await browser.newPage(); const uploads = [], remote = [];
    await page.route('**/*', route => { const url = new URL(route.request().url()); if (!['127.0.0.1', 'localhost'].includes(url.hostname)) { remote.push(url.href); return route.abort(); } return route.continue(); });
    page.on('request', r => { if (r.method() === 'POST') uploads.push(r.url()); });
    await page.goto('http://127.0.0.1:4183/tools/word-to-pdf');
    const choice = page.getByRole('checkbox', { name: /Use configured office renderer/ });
    assert.equal(await choice.isChecked(), false);
    await page.locator('input[type=file]').setInputFiles(resolve(root, 'sources', name + '.docx'));
    assert.deepEqual(uploads, []); await choice.check(); assert.deepEqual(uploads, []);
    await page.screenshot({ path: resolve(output, name + '-before.png'), fullPage: true });
    const start = performance.now(); await page.getByRole('button', { name: 'Convert to PDF', exact: true }).click();
    await page.getByText('Your file is ready').waitFor({ timeout: 150_000 });
    const download = page.waitForEvent('download'); await page.getByRole('button', { name: 'Save', exact: true }).click();
    await (await download).saveAs(resolve(output, name + '.pdf'));
    assert.deepEqual(uploads, [service + '/convert/docx-to-pdf']); assert.deepEqual(remote, []);
    await page.screenshot({ path: resolve(output, name + '-after.png'), fullPage: true });
    const bytes = await readFile(resolve(output, name + '.pdf')); assert(bytes.subarray(0, 5).toString() === '%PDF-');
    records.push({ name, milliseconds: performance.now()-start, uploads, externalRequests: remote, outputBytes: bytes.length }); await page.close();
  }
  // An unavailable service must never silently produce a plain-text PDF.
  const page = await browser.newPage(); await page.route('**/convert/docx-to-pdf', route => route.abort());
  await page.goto('http://127.0.0.1:4183/tools/word-to-pdf');
  await page.locator('input[type=file]').setInputFiles(resolve(root, 'sources/office-report.docx'));
  await page.getByRole('checkbox', { name: /Use configured office renderer/ }).check();
  await page.getByRole('button', { name: 'Convert to PDF', exact: true }).click();
  await page.getByRole('alert').waitFor(); assert.equal(await page.getByText('Your file is ready').count(), 0);
  await writeFile(resolve(output, 'verification.json'), JSON.stringify({ result: 'PASS', service, health, records, failedServiceHasNoTextFallback: true }, null, 2));
  console.log(JSON.stringify(records, null, 2));
} finally { if (browser) await browser.close(); server.kill(); }
