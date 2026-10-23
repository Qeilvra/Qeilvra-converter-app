// Actual UI downloads for the private CV and both table regressions, with no uploads.
import { createRequire } from 'node:module';
import { spawn } from 'node:child_process';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import assert from 'node:assert/strict';
const require = createRequire(new URL('../web/package.json', import.meta.url));
const { chromium } = require('@playwright/test'); const JSZip = require('jszip');
const root = resolve('.tools/professional-conversion'); const output = resolve(root, 'pdf-word-button'); await mkdir(output, { recursive: true });
const server = spawn(process.execPath, [resolve('web/node_modules/vite/bin/vite.js'), '--host', '127.0.0.1', '--port', '4184', '--strictPort'], { cwd: resolve('web'), env: { ...process.env, VITE_PROCESSING_API_URL: '' }, windowsHide: true, stdio: 'pipe' });
let logs = ''; server.stderr.on('data', d => { logs += d; }); let browser;
try {
  let ready = false;
  for (let i=0;i<100;i++) { if (server.exitCode !== null) throw new Error(logs); try { ready=(await fetch('http://127.0.0.1:4184/')).ok; } catch { } if(ready)break; await new Promise(r=>setTimeout(r,200)); }
  assert(ready,logs); browser=await chromium.launch();const records=[];
  for(const name of ['primary-cv','merged-table','office-export']) {
    const page=await browser.newPage();const remote=[],uploads=[];
    await page.route('**/*',route=>{const url=new URL(route.request().url());if(!['127.0.0.1','localhost'].includes(url.hostname)){remote.push(url.href);return route.abort();}return route.continue();});
    page.on('request',r=>{if(r.method()==='POST')uploads.push(r.url());});
    await page.goto('http://127.0.0.1:4184/tools/pdf-to-word');
    await page.locator('input[type=file]').setInputFiles(resolve(root,'sources',name+'.pdf'));
    await page.getByRole('button',{name:'Convert to DOCX'}).click();await page.getByText('Your file is ready').waitFor({timeout:60_000});
    const pending=page.waitForEvent('download');await page.getByRole('button',{name:'Save',exact:true}).click();await(await pending).saveAs(resolve(output,name+'.docx'));
    const actual=await JSZip.loadAsync(await readFile(resolve(output,name+'.docx'))), reference=await JSZip.loadAsync(await readFile(resolve(root,'browser-after',name+'.docx')));
    const model=await page.evaluate(({xml,expected})=>{
      const parse=text=>new DOMParser().parseFromString(text,'application/xml');const doc=parse(xml),baseline=parse(expected);
      const nodes=(d,name)=>[...d.getElementsByTagNameNS('*',name)];
      const text=d=>nodes(d,'t').map(n=>n.textContent).join('');
      const geometry=d=>nodes(d,'framePr').map(n=>[...n.attributes].map(a=>[a.name,a.value]));
      return {sameText:text(doc)===text(baseline),sameFrames:JSON.stringify(geometry(doc))===JSON.stringify(geometry(baseline)),tables:nodes(doc,'tbl').length,mergedCells:nodes(doc,'gridSpan').filter(n=>Number(n.getAttribute('w:val'))>1).length};
    },{xml:await actual.file('word/document.xml').async('string'),expected:await reference.file('word/document.xml').async('string')});
    assert(model.sameText&&model.sameFrames);assert.equal(model.tables,name==='primary-cv'?0:1);if(name!=='primary-cv')assert.equal(model.mergedCells,1);
    assert.deepEqual(remote,[]);assert.deepEqual(uploads,[]);records.push({name,...model,uploads,externalRequests:remote});
    await page.screenshot({path:resolve(output,name+'.png'),fullPage:true});await page.close();
  }
  await writeFile(resolve(output,'verification.json'),JSON.stringify({result:'PASS',records},null,2));console.log(JSON.stringify(records,null,2));
}finally{if(browser)await browser.close();server.kill();}
