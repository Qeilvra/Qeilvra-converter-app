// Reopen the actual native Java export with an independent XLSX reader.
// Requires: Android debug Java compilation, JDK21+, npm --prefix web ci.
import assert from 'node:assert/strict';
import { mkdir, writeFile, rm } from 'node:fs/promises';
import { resolve } from 'node:path';
import { spawnSync } from 'node:child_process';
import { createRequire } from 'node:module';
const require = createRequire(new URL('../web/package.json', import.meta.url));
const ExcelJS = require('exceljs');
const directory = resolve('.tools/native-xlsx-check');
const classes = resolve('android/app/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes');
await mkdir(directory, { recursive: true });
const source = resolve(directory, 'NativeXlsxFixture.java');
const output = resolve(directory, 'native.xlsx');
const tsv = 'ID\tValue\n001234\t92\n12345678901234567890\t=SUM(A1:A2)\n=== FileFlow table 2 ===\nName\tNotes\nAyesha\t"line one\nline two\twith tab"';
await writeFile(source, `import java.nio.file.*;
import com.fileflow.app.processing.OfficeOpenXml;
class NativeXlsxFixture {
  public static void main(String[] args) throws Exception {
    Files.write(Path.of(args[0]), OfficeOpenXml.createXlsxFromTsv(${JSON.stringify(tsv)}));
  }
}
`);
try {
  const result = spawnSync('java', ['--class-path', classes, source, output], { encoding: 'utf8', timeout: 60_000 });
  assert.equal(result.status, 0, result.stderr || result.error?.message || 'Java export failed; build Android first');
  const workbook = new ExcelJS.Workbook();
  await workbook.xlsx.readFile(output);
  assert.equal(workbook.worksheets.length, 2);
  assert.equal(workbook.worksheets[0].getCell('A2').value, '001234');
  assert.equal(workbook.worksheets[0].getCell('A3').value, '12345678901234567890');
  assert.equal(workbook.worksheets[0].getCell('B3').value, '=SUM(A1:A2)');
  assert.equal(workbook.worksheets[1].getCell('B2').value, 'line one\nline two\twith tab');
  console.log(JSON.stringify({ result: 'PASS', worksheets: 2, checked: ['leading zeros', 'long identifier', 'literal formula', 'embedded newline and tab'], scope: 'Actual pure-Java Android exporter plus independent ExcelJS parser; no device UI claim' }, null, 2));
} finally {
  await rm(source, { force: true });
  await rm(output, { force: true });
}
