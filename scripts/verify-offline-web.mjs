// Verify the built PWA in Chromium with networking disabled, including lazy engines and worker.
import { createRequire } from 'node:module';
import { spawn } from 'node:child_process';
import { readFile, writeFile, mkdir } from 'node:fs/promises';
import { resolve } from 'node:path';
import assert from 'node:assert/strict';
const require = createRequire(new URL('../web/package.json', import.meta.url));
const { chromium } = require('@playwright/test');
const ExcelJS = require('exceljs'); const JSZip = require('jszip');
const server = spawn(process.execPath, [resolve('web/node_modules/vite/bin/vite.js'),'preview','--host','127.0.0.1','--port','4175','--strictPort'], { cwd:resolve('web'), windowsHide:true, stdio:'pipe' });
let logs = ''; server.stderr.on('data', data => { logs += data; });
let browser;
try {
  let ready = false;
  for (let attempt = 0; attempt < 100; attempt++) {
    if (server.exitCode !== null) throw new Error(`Preview failed: ${logs}`);
    try { const response = await fetch('http://127.0.0.1:4175/'); ready = response.ok; } catch { }
    if (ready) break; await new Promise(resolve => setTimeout(resolve,200));
  }
  assert(ready, 'Production preview did not start');
  browser = await chromium.launch(); const context = await browser.newContext({serviceWorkers:'allow'}); const page = await context.newPage();
  const source = await readFile('.tools/backend-free-evidence/chromium-desktop/source.pdf');
  const api = []; page.on('request', request => { if(request.url().includes('/api/v1')) api.push(request.url()); });
  await page.goto('http://127.0.0.1:4175/');
  await page.evaluate(async () => { await navigator.serviceWorker.ready; });
  await page.reload();
  await page.waitForFunction(() => !!navigator.serviceWorker.controller);
  await context.setOffline(true);
  await page.goto('http://127.0.0.1:4175/tools/pdf-to-excel');
  await page.locator('input[type=file]').setInputFiles({name:'offline.pdf',mimeType:'application/pdf',buffer:source});
  await page.getByRole('button',{name:'Detect tables'}).click();
  await page.getByLabel('Table 1, row 2, column 1').waitFor();
  assert.equal(await page.getByLabel('Table 1, row 2, column 1').inputValue(),'001234');
  await page.getByRole('button',{name:'Export Excel workbook'}).click();
  let pending = page.waitForEvent('download'); await page.getByRole('button',{name:'Save',exact:true}).click();
  const workbook = new ExcelJS.Workbook(); await workbook.xlsx.readFile(await (await pending).path());
  assert.equal(workbook.worksheets.length,2); assert.equal(workbook.worksheets[0].getCell('A3').value,'12345678901234567890');
  await page.goto('http://127.0.0.1:4175/tools/pdf-to-word');
  await page.locator('input[type=file]').setInputFiles({name:'offline.pdf',mimeType:'application/pdf',buffer:source});
  await page.getByRole('button',{name:'Convert to DOCX'}).click();
  pending = page.waitForEvent('download'); await page.getByRole('button',{name:'Save',exact:true}).click();
  const zip = await JSZip.loadAsync(await readFile(await (await pending).path()));
  assert((await zip.file('word/document.xml').async('string')).includes('001234'));
  assert.equal(api.length,0);
  const result = { result:'PASS', mode:'Production PWA after initial install; Chromium network disabled', checks:['deep-link navigation','PDF.js worker','PDF text/table extraction','ExcelJS XLSX export reopened','editable DOCX export reopened','no processing API requests'] };
  await mkdir('.tools/backend-free-evidence',{recursive:true}); await writeFile('.tools/backend-free-evidence/offline-web.json',JSON.stringify(result,null,2));
  console.log(JSON.stringify(result,null,2));
} finally { if(browser) await browser.close(); server.kill(); }
