// Mandatory build preparation. Downloads code, never documents; no accounts or API keys.
import { createHash } from 'node:crypto';
import { get } from 'node:https';
import { brotliDecompressSync, gunzipSync, gzipSync } from 'node:zlib';
import { readFile, mkdir, writeFile, lstat, realpath, rm, rename, mkdtemp } from 'node:fs/promises';
import { resolve, sep } from 'node:path';
const webRoot = resolve(import.meta.dirname, '..');
const lock = JSON.parse(await readFile(resolve(import.meta.dirname,'office-runtime-lock.json'),'utf8'));
const destination = resolve(webRoot,'public/office-runtime');
const cache = resolve(webRoot,'node_modules/.cache/fileflow-office');
const local = process.argv.find(a=>a.startsWith('--from-dir='))?.slice('--from-dir='.length);
const hash = data => createHash('sha256').update(data).digest('hex');
const valid = (bytes,file) => bytes.length===file.bytes && hash(bytes)===file.sha256;
await mkdir(cache,{recursive:true});
async function download(url, redirects=0) {
  if(redirects>4 || !url.startsWith('https://'))throw Error('Invalid office runtime redirect');
  return new Promise((resolve,reject)=>{
    const request=get(url,{headers:{'Accept-Encoding':'identity'}},response=>{
      if([301,302,303,307,308].includes(response.statusCode)) {response.resume();resolve(download(new URL(response.headers.location,url).href,redirects+1));return}
      if(response.statusCode!==200){response.resume();reject(Error(`Office runtime download failed (${response.statusCode}): ${url}`));return}
      const chunks=[];let size=0;
      response.on('data',data=>{size+=data.length;if(size>300*1024*1024)response.destroy(Error('Runtime download exceeds limit'));else chunks.push(data)});
      response.on('error',reject);response.on('end',()=>resolve({bytes:Buffer.concat(chunks),encoding:response.headers['content-encoding']}));
    });
    request.setTimeout(30_000,()=>request.destroy(Error(`Office runtime download timed out: ${url}`)));
    request.on('error',reject);
  });
}
const assets=[];
const stage=await mkdtemp(resolve(cache,'package-'));
for(const file of lock.files) {
  let bytes;
  // Reuse only content-verified code. This also migrates the earlier local install.
  for(const candidate of [local && resolve(local,file.name),resolve(cache,file.sha256),resolve(destination,file.name)].filter(Boolean)) {
    try {const data=await readFile(candidate);if(valid(data,file)){bytes=data;break}} catch {}
  }
  if(!bytes) {
    if(local)throw Error(`Missing or invalid reviewed runtime file: ${file.name}`);
    console.log('Downloading reviewed office engine:',file.name);
    const response=await download(file.url);bytes=response.bytes;
    // Some CDN responses label already-decoded bytes "br". The digest decides.
    if(!valid(bytes,file) && response.encoding==='br')bytes=brotliDecompressSync(bytes,{maxOutputLength:file.bytes+1});
    else if(!valid(bytes,file) && response.encoding==='gzip')bytes=gunzipSync(bytes,{maxOutputLength:file.bytes+1});
  }
  if(!valid(bytes,file))throw Error(`Office runtime checksum mismatch: ${file.name}. Build stopped; review the upstream update.`);
  await writeFile(resolve(cache,file.sha256),bytes);
  const compressed=['soffice.wasm','soffice.data'].includes(file.name);
  const payload=compressed ? gzipSync(bytes,{level:9}) : bytes;
  // .gz is treated as Content-Encoding by common static servers (including
  // Vite preview). .gzip is an ordinary payload, decoded explicitly in-browser.
  const path=file.name+(compressed?'.gzip':'');
  await writeFile(resolve(stage,path),payload);
  assets.push({...file,path,encoding:compressed?'gzip':'identity',downloadBytes:payload.length,downloadSha256:hash(payload)});
  console.log('Verified and packaged',path,payload.length,'bytes');
}
// Version the application's UNO worker with the exact engine it imports. A
// service-worker response can discard URL fragments, so never encode its
// runtime dependency in a fragment or an unversioned global manifest lookup.
const worker=await readFile(resolve(webRoot,'public/office-renderer/office-thread.js'));
await writeFile(resolve(stage,'office-thread.js'),worker);
assets.push({name:'office-thread.js',path:'office-thread.js',encoding:'identity',bytes:worker.length,sha256:hash(worker),downloadBytes:worker.length,downloadSha256:hash(worker)});
const notice=await readFile(resolve(webRoot,'licenses/office-runtime-notices.md'));
await writeFile(resolve(stage,'NOTICE.txt'),notice);
const version='v-'+hash(JSON.stringify([...assets.map(a=>[a.path,a.downloadSha256]),['NOTICE.txt',hash(notice)]])).slice(0,16);
const packageRoot=await mkdtemp(resolve(cache,'ready-'));
await rename(stage,resolve(packageRoot,version));
const manifest={...lock,format:'fileflow-office-gzip-v2',assetBase:`/office-runtime/${version}/`,files:assets};
await writeFile(resolve(packageRoot,'manifest.json'),JSON.stringify(manifest,null,2)+'\n');
// This directory contains generated code only. Check its real absolute location
// before recursive replacement; never touch fixtures, caches, or arbitrary paths.
if(destination!==resolve(webRoot,'public','office-runtime') || !destination.startsWith(webRoot+sep))throw Error('Unsafe runtime output path');
try {
  const info=await lstat(destination);
  if(info.isSymbolicLink() || !(await realpath(destination)).startsWith((await realpath(resolve(webRoot,'public')))+sep))throw Error('Runtime output must stay inside public/');
} catch(error) {if(error.code!=='ENOENT')throw error}
await rm(destination,{recursive:true,force:true});
await rename(packageRoot,destination);
console.log(`Office runtime ready: ${assets.reduce((s,a)=>s+a.downloadBytes,0)} download bytes; ${version}.`);
