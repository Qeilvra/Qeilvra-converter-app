// Explicit developer setup, never run by npm install/build or by the application.
// Downloads public engine code only. No API keys, accounts or document uploads.
import { createHash } from 'node:crypto';
import { get } from 'node:https';
import { brotliDecompressSync, gunzipSync } from 'node:zlib';
import { readFile, mkdir, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
const root = resolve(import.meta.dirname, '..');
const lock = JSON.parse(await readFile(resolve(root,'scripts/office-runtime-lock.json'),'utf8'));
const destination = resolve(root,'web/public/office-runtime');
const local = process.argv.find(a=>a.startsWith('--from-dir='))?.slice('--from-dir='.length);
const hash = data => createHash('sha256').update(data).digest('hex');
async function download(url) {
  return new Promise((resolve,reject)=>{
    get(url,{headers:{'Accept-Encoding':'identity'}},response=>{
      if(response.statusCode!==200){response.resume();reject(Error(`Download failed (${response.statusCode}): ${url}`));return}
      const chunks=[];let size=0;
      response.on('data',data=>{size+=data.length;if(size>300*1024*1024)response.destroy(Error('Runtime download exceeds limit'));else chunks.push(data)});
      response.on('error',reject);response.on('end',()=>resolve({bytes:Buffer.concat(chunks),encoding:response.headers['content-encoding']}));
    }).on('error',reject);
  });
}
await mkdir(destination,{recursive:true});
// Invalidate an existing installation first; a partial update must never look ready.
await writeFile(resolve(destination,'manifest.json'),JSON.stringify({engine:'incomplete'}));
for(const file of lock.files) {
  let bytes;
  if(local) bytes=await readFile(resolve(local,file.name));
  else {
    const response=await download(file.url);bytes=response.bytes;
    // The CDN currently labels already-decoded responses "br". Trust neither the
    // label nor a magic signature: the reviewed content digest is authoritative.
    if(hash(bytes)!==file.sha256 && response.encoding==='br') bytes=brotliDecompressSync(bytes);
    else if(hash(bytes)!==file.sha256 && response.encoding==='gzip') bytes=gunzipSync(bytes);
  }
  if(bytes.length!==file.bytes || hash(bytes)!==file.sha256)throw Error(`Runtime checksum mismatch: ${file.name}. Review the upstream update; do not silently change the lock file.`);
  await writeFile(resolve(destination,file.name),bytes);console.log('Verified',file.name,bytes.length);
}
await writeFile(resolve(destination,'NOTICE.txt'),await readFile(resolve(root,'docs/office-runtime-notices.md')));
await writeFile(resolve(destination,'manifest.json'),JSON.stringify(lock,null,2));
console.log('Local office runtime ready. This is a large developer evaluation package. Review third-party distribution obligations before publishing it.');
