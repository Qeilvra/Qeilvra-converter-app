// Actual compiled Java production writer, synthetic grid model. No Android extraction claim.
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { resolve, delimiter } from 'node:path';
import { spawnSync } from 'node:child_process';
import { createRequire } from 'node:module';
import assert from 'node:assert/strict';
const require = createRequire(new URL('../web/package.json', import.meta.url));
const JSZip = require('jszip');
const output = resolve('.tools/professional-conversion/native-writer'); await mkdir(output, { recursive: true });
const source = resolve(output, 'NativeGridFixture.java');
await writeFile(source, `package com.fileflow.app.processing;
import java.nio.file.*;
import java.util.*;
class NativeGridFixture {
  static LocalDocumentModel.Span span(String text,float x,float y,float size,float width,String font) {
    LocalDocumentModel.Span s=new LocalDocumentModel.Span(text,x,y,width,size);s.font=font;return s;
  }
  static WordLayout.Rule rule(float x,float y,float width,float height) {
    WordLayout.Rule r=new WordLayout.Rule();r.x=x;r.y=y;r.width=width;r.height=height;r.thickness=.75f;r.color="000000";return r;
  }
  public static void main(String[] args) throws Exception {
    LocalWordDocument.Page p=new LocalWordDocument.Page();p.width=612;p.height=792;
    List<LocalDocumentModel.Span> spans=new ArrayList<>();
    spans.add(span("MERGED AND MULTILINE TABLE",50,60,18,282,"Helvetica-Bold"));
    spans.add(span("Quarterly summary",58,122,10,85,"Helvetica"));
    spans.add(span("001234",58,155,10,33.36f,"Helvetica"));
    spans.add(span("42.50",273,155,10,25.02f,"Helvetica"));
    spans.add(span("First cell line",418,155,10,52.78f,"Helvetica"));
    spans.add(span("Second cell line",418,170,10,67.82f,"Helvetica"));
    spans.add(span("009876",58,217,10,33.36f,"Helvetica"));
    spans.add(span("87.00",273,217,10,25.02f,"Helvetica"));
    spans.add(span("Another row",418,217,10,53.91f,"Helvetica"));
    spans.add(span("Text below the table remains independently editable.",50,280,11,246,"Helvetica"));
    p.lines=LocalDocumentModel.group(spans);
    for(float y:new float[]{100,135,195,230})p.rules.add(rule(50,y,510,0));
    p.rules.add(rule(50,100,0,130));p.rules.add(rule(560,100,0,130));
    p.rules.add(rule(265,135,0,95));p.rules.add(rule(410,135,0,95));
    Files.write(Path.of(args[0]),LocalWordDocument.create(Collections.singletonList(p)));
  }
}
`);
const classes = resolve('android/app/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes');
const cp = [classes, resolve('.tools/android-pdfbox-audit.jar')].join(delimiter);
const resultPath = resolve(output, 'merged-table.docx');
for (const [cmd, args] of [['javac', ['-encoding', 'UTF-8', '-cp', cp, '-d', output, source]], ['java', ['-cp', [output, cp].join(delimiter), 'com.fileflow.app.processing.NativeGridFixture', resultPath]]]) {
  const r = spawnSync(cmd, args, { encoding: 'utf8', timeout: 60_000, windowsHide: true }); assert.equal(r.status, 0, r.stderr || r.error?.message);
}
const zip = await JSZip.loadAsync(await readFile(resultPath)); const xml = await zip.file('word/document.xml').async('string');
assert.equal((xml.match(/<w:tbl>/g) ?? []).length, 1); assert(xml.includes('w:gridSpan w:val="3"'));
assert(xml.includes('First cell line') && xml.includes('Second cell line') && xml.includes('<w:br/>'));
await writeFile(resolve(output, 'verification.json'), JSON.stringify({ result: 'PASS', scope: 'Compiled native Java table topology and OOXML writer, synthetic input model. PDFBox extraction and Android UI require a device.', cells: 7, mergedColumns: 3 }, null, 2));
console.log('Native grid DOCX generated and independently inspected. Render it before judging fidelity.');
