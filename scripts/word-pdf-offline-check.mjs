// Verify the production PWA, actual conversion button, saved PDF and a second fully offline conversion.
import { createRequire } from 'node:module';
import { spawn } from 'node:child_process';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import assert from 'node:assert/strict';
const require = createRequire(new URL('../web/package.json', import.meta.url));
const { chromium } = require('@playwright/test'); const { PDFDocument } = require('pdf-lib');
const root = resolve('.tools/word-pdf-rendering'), output=resolve(root,'production-offline');await mkdir(output,{recursive:true});
const server=spawn(process.execPath,[resolve('web/node_modules/vite/bin/vite.js'),'preview','--host','127.0.0.1','--port','4187','--strictPort'],{cwd:resolve('web'),windowsHide:true,stdio:'pipe'});
let logs='';server.stderr.on('data',d=>logs+=d);let browser;
try {
  let ready=false;for(let i=0;i<100;i++){try{ready=(await fetch('http://127.0.0.1:4187/')).ok}catch{}if(ready)break;await new Promise(r=>setTimeout(r,200))}assert(ready,logs);
  browser=await chromium.launch(); const context=await browser.newContext({serviceWorkers:'allow'}),page=await context.newPage();
  const external=[],posts=[];
  context.on('request',r=>{const u=new URL(r.url());if(['http:','https:'].includes(u.protocol) && u.hostname!=='127.0.0.1')external.push(r.url());if(r.method()==='POST')posts.push(r.url())});
  await page.goto('http://127.0.0.1:4187/tools/word-to-pdf');
  await page.evaluate(()=>navigator.serviceWorker.ready);await page.reload();
  await page.waitForFunction(()=>navigator.serviceWorker.controller!==null);
  const records=[];
  for(const offline of [false,true]) {
    if(offline){await context.setOffline(true);await page.reload()}
    await page.locator('.dropzone input[type=file]').setInputFiles(resolve(root,'sources/complex-report.docx'));
    await page.getByLabel('Matching local fonts').setInputFiles(['arial.ttf','arialbd.ttf','calibri.ttf','calibrib.ttf'].map(n=>resolve('C:/Windows/Fonts',n)));
    const start=performance.now();await page.getByRole('button',{name:'Convert to PDF',exact:true}).click();
    await page.getByText('Your file is ready').waitFor({timeout:180_000});
    const pending=page.waitForEvent('download');await page.getByRole('button',{name:'Save',exact:true}).click();
    const path=resolve(output,offline?'offline.pdf':'warm.pdf');await(await pending).saveAs(path);
    const pdf=await PDFDocument.load(await readFile(path));assert.equal(pdf.getPageCount(),2);
    const cacheKeys=await page.evaluate(async()=>{const cache=await caches.open('fileflow-office-runtime-v1');return(await cache.keys()).map(r=>new URL(r.url).pathname)});
    assert.equal(cacheKeys.length,7);
    assert.equal(await page.locator('iframe[title="Offline document renderer"]').count(),0);
    records.push({offline,milliseconds:performance.now()-start,pages:pdf.getPageCount(),cacheKeys});
    await page.screenshot({path:resolve(output,offline?'offline.png':'warm.png'),fullPage:true});
  }
  assert.deepEqual(external,[]);assert.deepEqual(posts,[]);
  await writeFile(resolve(output,'results.json'),JSON.stringify({result:'PASS',records,externalRequests:external,posts},null,2));console.log(records);
} finally {if(browser)await browser.close();server.kill()}
