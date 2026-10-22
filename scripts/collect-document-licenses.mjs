// Preserve the resolved direct document engines' license texts in the static distribution.
// This is not a complete transitive dependency SBOM.
import { readFile, readdir, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
const parts = ['FileFlow document engine license notices\nRegenerate after dependency updates with: node scripts/collect-document-licenses.mjs\n'];
for (const name of ['pdfjs-dist','docx','exceljs','jszip','pdf-lib','jspdf']) {
  const directory = resolve('web/node_modules',name);
  const pkg = JSON.parse(await readFile(resolve(directory,'package.json'),'utf8'));
  const licenses = (await readdir(directory)).filter(name=>/^(LICENSE|NOTICE)(\.|$)/i.test(name));
  if (!licenses.length) throw new Error(`No license text found for ${name}`);
  parts.push(`\n${'='.repeat(70)}\n${name} ${pkg.version} (${pkg.license})\n`);
  for (const file of licenses) parts.push(await readFile(resolve(directory,file),'utf8'));
}
await writeFile(resolve('web/public/third-party-document-notices.txt'),parts.join('\n'));
console.log('Collected six direct document-engine license notices.');
