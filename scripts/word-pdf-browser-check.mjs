// Exercise actual FileFlow selection, conversion and Save controls; block all outside hosts.
import { createRequire } from 'node:module';
import { spawn } from 'node:child_process';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import assert from 'node:assert/strict';
const require = createRequire(new URL('../web/package.json', import.meta.url));
const { chromium } = require('@playwright/test');
const phase = process.argv[2] || 'before';
assert(['before','after'].includes(phase));
const root = resolve('.tools/word-pdf-rendering');
const output = resolve(root,process.argv.find(a=>a.startsWith('--output='))?.slice('--output='.length) || 'browser-'+phase); await mkdir(output,{recursive:true});
const server = spawn(process.execPath,[resolve('web/node_modules/vite/bin/vite.js'),'--host','127.0.0.1','--port','4186','--strictPort'],{cwd:resolve('web'),windowsHide:true,stdio:'pipe'});
let logs=''; server.stderr.on('data',d=>logs+=d); let browser;
try {
  let ready=false; for(let i=0;i<100;i++){try{ready=(await fetch('http://127.0.0.1:4186/')).ok}catch{} if(ready)break;await new Promise(r=>setTimeout(r,200));} assert(ready,logs);
  browser=await chromium.launch({args:['--enable-precise-memory-info']});
  const sharedPage=process.argv.includes('--reuse-page') ? await browser.newPage() : null;
  let records=[]; try { records=JSON.parse(await readFile(resolve(output,'results.json'),'utf8')); } catch {}
  const selected = process.argv.find(a=>a.startsWith('--sample='))?.slice('--sample='.length);
  for(const name of ['professional-cv','columns','complex-report','urdu-rtl','shapes'].filter(n=>!selected || selected===n)) {
    const page=sharedPage || await browser.newPage(); const blocked=[], uploads=[];
    await page.addInitScript(()=>{window.officeMemory=null;addEventListener('message',e=>{if(e.origin===location.origin && e.data?.type==='pdf')try {window.officeMemory={wasmLinearMemoryBytes:e.source.HEAP8?.buffer.byteLength ?? null,jsHeapBytes:performance.memory?.usedJSHeapSize ?? null,pthreadType:typeof e.source.PThread,disposeType:typeof e.source.fileflowDispose}}catch{}})});
    await page.route('**/*',route=>{const r=route.request(),u=new URL(r.url());if(r.method()==='POST')uploads.push(u.href);if(u.hostname!=='127.0.0.1'){blocked.push(u.href);return route.abort()}return route.continue()});
    if(!sharedPage || !page.url().startsWith('http://127.0.0.1:4186/')) await page.goto('http://127.0.0.1:4186/tools/word-to-pdf');
    await page.locator('.dropzone').waitFor();
    if(phase==='before' && await page.getByRole('checkbox',{name:'Export basic text only (discard document layout)'}).count()) await page.getByRole('checkbox',{name:'Export basic text only (discard document layout)'}).check();
    await page.locator('.dropzone input[type=file]').setInputFiles(resolve(root,'sources',name+'.docx'));
    if(phase==='after' && process.argv.includes('--system-fonts')) await page.getByLabel('Matching local fonts').setInputFiles(['arial.ttf','arialbd.ttf','ariali.ttf','arialbi.ttf','GARA.TTF','GARABD.TTF','GARAIT.TTF','times.ttf','timesbd.ttf','timesi.ttf','timesbi.ttf','calibri.ttf','calibrib.ttf','calibrii.ttf','calibriz.ttf'].map(n=>resolve('C:/Windows/Fonts',n)));
    await page.evaluate(()=>{window.officeMemory=null});
    const start=performance.now();
    await page.getByRole('button',{name:phase==='before'?'Export basic text PDF':'Convert to PDF',exact:true}).click();
    await Promise.race([page.getByText('Your file is ready').waitFor({timeout:180_000}),page.getByRole('alert').waitFor({timeout:180_000})]);
    const record={name,milliseconds:performance.now()-start,uploads,blockedExternalRequests:blocked};
    record.memory=await page.evaluate(()=>window.officeMemory ?? {jsHeapBytes:performance.memory?.usedJSHeapSize ?? null,wasmLinearMemoryBytes:0});
    await page.waitForTimeout(500);
    const session=await page.context().newCDPSession(page);
    let workerTargets=[];
    for(let attempt=0;attempt<20;attempt++){
      workerTargets=(await session.send('Target.getTargets')).targetInfos.filter(t=>t.type==='worker');
      if(!workerTargets.length)break;await page.waitForTimeout(250);
    }
    record.remainingWorkers=workerTargets.length;
    if(workerTargets.length) {
      console.log('Worker diagnostics',record.memory,workerTargets.map(t=>({url:t.url,type:t.type})));
      const control=await browser.newBrowserCDPSession();
      control.on('Target.receivedMessageFromTarget',m=>console.log('Worker runtime',m.message));
      for(const target of workerTargets) {
        const {sessionId}=await control.send('Target.attachToTarget',{targetId:target.targetId,flatten:false});
        await control.send('Target.sendMessageToTarget',{sessionId,message:JSON.stringify({id:1,method:'Runtime.evaluate',params:{expression:'({name:self.name,hasModule:typeof Module,workerCount:typeof PThread!=="undefined"?PThread.runningWorkers.length:null})',returnByValue:true}})});
      }
      await page.waitForTimeout(500);await control.detach();
    }
    await session.detach();
    record.remainingEngineFrames=await page.locator('iframe[title="Offline document renderer"]').count();
    assert.equal(record.remainingEngineFrames,0); assert.equal(record.remainingWorkers,0);
    if(await page.getByText('Your file is ready').count()) {
      const pending=page.waitForEvent('download');await page.getByRole('button',{name:'Save',exact:true}).click();await(await pending).saveAs(resolve(output,name+'.pdf'));
      const bytes=await readFile(resolve(output,name+'.pdf'));assert.equal(bytes.subarray(0,5).toString(),'%PDF-');record.bytes=bytes.length;
    } else record.error=await page.getByRole('alert').innerText();
    await page.screenshot({path:resolve(output,name+'.png'),fullPage:true});assert.deepEqual(uploads,[]);assert.deepEqual(blocked,[]);
    records=records.filter(r=>r.name!==name);records.push(record); console.log(record);await writeFile(resolve(output,'results.json'),JSON.stringify(records,null,2));if(!sharedPage)await page.close();
  }
} finally {if(browser)await browser.close();server.kill();}
