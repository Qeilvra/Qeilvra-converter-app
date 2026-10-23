// Verify the production PWA, actual conversion button, saved PDF and a second fully offline conversion.
import { createRequire } from 'node:module';
import { spawn } from 'node:child_process';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import assert from 'node:assert/strict';
const require = createRequire(new URL('../web/package.json', import.meta.url));
const { chromium } = require('@playwright/test'); const { PDFDocument } = require('pdf-lib');
// This option tests an already deployed URL; it never creates a deployment.
const remote=process.argv.find(a=>a.startsWith('--base-url='))?.slice(11);
const base=new URL(remote || 'http://127.0.0.1:4187');
assert(base.protocol==='https:' || base.hostname==='127.0.0.1');
const root = resolve('.tools/word-pdf-rendering'), output=resolve('.tools/production-packaging',remote?'deployed-browser':'browser');await mkdir(output,{recursive:true});
const server=remote?null:spawn(process.execPath,[resolve('web/node_modules/vite/bin/vite.js'),'preview','--host','127.0.0.1','--port','4187','--strictPort'],{cwd:resolve('web'),windowsHide:true,stdio:'pipe'});
let logs='';server?.stderr.on('data',d=>logs+=d);let browser;
try {
  let ready=false;for(let i=0;i<100;i++){if(server && server.exitCode!==null)throw Error(logs);try{ready=(await fetch(base)).ok}catch{}if(ready)break;await new Promise(r=>setTimeout(r,200))}assert(ready,logs);
  const manifestResponse=await fetch(new URL('/office-runtime/manifest.json',base));assert.equal(manifestResponse.status,200);assert.match(manifestResponse.headers.get('content-type'),/application\/json/);
  const manifest=await manifestResponse.json();assert.equal(manifest.format,'fileflow-office-gzip-v2');
  const missing=[];for(const path of ['/office-runtime/missing.wasm','/office-renderer/missing.js','/office-runtime/soffice.wasm']){const response=await fetch(new URL(path,base));assert.equal(response.status,404,`SPA fallback intercepted ${path}`);missing.push({path,status:response.status})}
  browser=await chromium.launch(); const context=await browser.newContext({serviceWorkers:'allow'}),page=await context.newPage();
  const external=[],posts=[], diagnostics=[],runtimeResponses=[],ui=[];
  page.on('console',m=>{if(m.type()==='error')diagnostics.push(m.text())});
  page.on('pageerror',e=>{diagnostics.push(e.message);console.error(e.message)});
  context.on('response',r=>{if(r.url().includes('/office-'))runtimeResponses.push({url:r.url(),status:r.status(),type:r.headers()['content-type'],encoding:r.headers()['content-encoding']||null,fromServiceWorker:r.fromServiceWorker()})});
  context.on('request',r=>{const u=new URL(r.url());if(['http:','https:'].includes(u.protocol) && u.origin!==base.origin)external.push(r.url());if(r.method()==='POST')posts.push(r.url())});
  await page.goto(new URL('/tools/word-to-pdf',base).href);
  await page.evaluate(()=>navigator.serviceWorker.ready);await page.reload();
  await page.waitForFunction(()=>navigator.serviceWorker.controller!==null);
  await page.locator('.office-options').waitFor();
  assert(await page.evaluate(()=>crossOriginIsolated));
  // Reproduce the old deployed SPA response cached as an engine manifest.
  await page.evaluate(async()=>{const old=await caches.open('fileflow-office-runtime-v1');await old.put('/office-runtime/manifest.json',new Response('<html>old SPA fallback</html>',{headers:{'content-type':'text/html'}}))});
  for(const width of [320,390,1920]){
    await page.setViewportSize({width,height:1080});
    const boxes=await page.evaluate(()=>{const box=selector=>{const r=document.querySelector(selector).getBoundingClientRect();return {x:r.x,y:r.y,right:r.right,bottom:r.bottom}};return {choice:box('.office-choice'),fonts:box('.office-fonts'),field:box('.office-fonts input'),scroll:document.documentElement.scrollWidth,width:innerWidth}});
    assert(boxes.choice.bottom<boxes.fonts.y,'Font label overlaps checkbox');assert(boxes.field.right<=width && boxes.field.x>=0,'Font upload overflows');assert(boxes.scroll<=width,'Page overflows');ui.push({width,...boxes});
    await page.screenshot({path:resolve(output,`controls-${width}.png`),fullPage:true});
  }
  await page.setViewportSize({width:1440,height:1080});
  const records=[];
  for(const offline of [false,true]) {
    if(offline){await context.setOffline(true);await page.reload()}
    await page.locator('.dropzone input[type=file]').setInputFiles(resolve(root,'sources/complex-report.docx'));
    await page.getByLabel('Matching local fonts').setInputFiles(['arial.ttf','arialbd.ttf','calibri.ttf','calibrib.ttf'].map(n=>resolve('C:/Windows/Fonts',n)));
    const start=performance.now();await page.getByRole('button',{name:'Convert to PDF',exact:true}).click();
    await Promise.race([page.getByText('Your file is ready').waitFor({timeout:200_000}),page.getByRole('alert').waitFor({timeout:200_000}).then(async()=>{throw Error(await page.getByRole('alert').innerText())})]).catch(async error=>{await page.screenshot({path:resolve(output,'failure.png'),fullPage:true});await writeFile(resolve(output,'failure.json'),JSON.stringify({error:error.message,diagnostics,runtimeResponses},null,2));throw error});
    const pending=page.waitForEvent('download');await page.getByRole('button',{name:'Save',exact:true}).click();
    const path=resolve(output,offline?'offline.pdf':'warm.pdf');await(await pending).saveAs(path);
    const pdf=await PDFDocument.load(await readFile(path));assert.equal(pdf.getPageCount(),2);
    const cacheKeys=await page.evaluate(async()=>{const entries=[];for(const name of ['fileflow-office-manifest-v2','fileflow-office-runtime-v2']){const cache=await caches.open(name);entries.push(...(await cache.keys()).map(r=>new URL(r.url).pathname))}return entries});
    assert.equal(cacheKeys.length,8);
    assert.equal(await page.locator('iframe[title="Offline document renderer"]').count(),0);
    records.push({offline,milliseconds:performance.now()-start,pages:pdf.getPageCount(),cacheKeys});
    await page.screenshot({path:resolve(output,offline?'offline.png':'warm.png'),fullPage:true});
  }
  assert.deepEqual(external,[]);assert.deepEqual(posts,[]);
  assert(runtimeResponses.every(r=>r.status===200),'A required engine request failed');
  for(const file of manifest.files)assert(runtimeResponses.some(r=>new URL(r.url).pathname===manifest.assetBase+file.path),`No request for ${file.path}`);
  assert(!runtimeResponses.some(r=>/soffice\.(wasm|data)$/.test(new URL(r.url).pathname)),'Raw engine was requested');
  const failedContext=await browser.newContext({serviceWorkers:'block'}),failedPage=await failedContext.newPage();
  await failedPage.route('**/*.gzip',route=>route.fulfill({status:200,contentType:'text/html',body:'<html>SPA fallback</html>'}));
  await failedPage.goto(new URL('/tools/word-to-pdf',base).href);
  await failedPage.locator('.dropzone input[type=file]').setInputFiles(resolve(root,'sources/complex-report.docx'));
  await failedPage.getByRole('button',{name:'Convert to PDF',exact:true}).click();await failedPage.getByRole('alert').waitFor();
  assert.match(await failedPage.getByRole('alert').innerText(),/could not be downloaded/);assert.equal(await failedPage.getByRole('button',{name:'Save',exact:true}).count(),0);assert.equal(await failedPage.locator('iframe[title="Offline document renderer"]').count(),0);
  await writeFile(resolve(output,'results.json'),JSON.stringify({result:'PASS',base:base.href,records,ui,missing,externalRequests:external,posts,diagnostics,runtimeResponses,staleV1CacheIgnored:true,htmlRuntimeRejectedWithoutFallback:true},null,2));console.log(records);
} finally {if(browser)await browser.close();server?.kill()}
