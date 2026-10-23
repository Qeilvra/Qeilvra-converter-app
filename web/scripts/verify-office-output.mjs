// A successful build must include a complete, compatible, size-checked engine.
import { readFile, readdir, stat } from 'node:fs/promises';
import { resolve, relative } from 'node:path';
import { createHash } from 'node:crypto';
import { gunzipSync } from 'node:zlib';
import assert from 'node:assert/strict';
const output=resolve(import.meta.dirname,'../dist');
const manifest=JSON.parse(await readFile(resolve(output,'office-runtime/manifest.json'),'utf8'));
assert.equal(manifest.format,'fileflow-office-gzip-v2');
assert.match(manifest.assetBase,/^\/office-runtime\/v-[a-f0-9]{16}\/$/);
const hash=data=>createHash('sha256').update(data).digest('hex');
for(const file of manifest.files) {
  const bytes=await readFile(resolve(output,manifest.assetBase.slice(1),file.path));
  assert.equal(bytes.length,file.downloadBytes,file.path);assert.equal(hash(bytes),file.downloadSha256,file.path);
  const decoded=file.encoding==='gzip'?gunzipSync(bytes,{maxOutputLength:file.bytes+1}):bytes;
  assert.equal(decoded.length,file.bytes,file.name);assert.equal(hash(decoded),file.sha256,file.name);
}
const files=[];
async function walk(path){for(const entry of await readdir(path,{withFileTypes:true})){const file=resolve(path,entry.name);if(entry.isDirectory())await walk(file);else files.push({path:relative(output,file).replaceAll('\\','/'),bytes:(await stat(file)).size})}}
await walk(output);
for(const file of files)assert(file.bytes<100_000_000,`Production asset exceeds conservative 100 MB ceiling: ${file.path}`);
const total=files.reduce((s,f)=>s+f.bytes,0);
assert(total<100_000_000,`Static output exceeds conservative 100 MB budget: ${total}`);
assert(!files.some(f=>/^office-runtime\/soffice\.(wasm|data)$/.test(f.path)),'Uncompressed legacy engine leaked into output');
console.log(JSON.stringify({result:'PASS',outputBytes:total,largest:files.sort((a,b)=>b.bytes-a.bytes).slice(0,5),engine:manifest.engineBuild,version:manifest.assetBase},null,2));
