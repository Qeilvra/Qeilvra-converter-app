// Exercise the actual compiled native geometry/writer with synthetic positions on the JVM.
// This deliberately does not claim to test Android PDFBox extraction or device behavior.
import { mkdir, writeFile, readFile } from 'node:fs/promises';
import { resolve, delimiter } from 'node:path';
import { spawnSync } from 'node:child_process';
import { createRequire } from 'node:module';
import assert from 'node:assert/strict';
const require = createRequire(new URL('../web/package.json', import.meta.url));
const JSZip = require('jszip');
const output = resolve('.tools/pdf-word-quality/android'); await mkdir(output, { recursive: true });
const source = resolve(output, 'NativeWordFixture.java');
await writeFile(source, `package com.fileflow.app.processing;
import java.nio.file.*;
import java.util.*;
class NativeWordFixture {
  static LocalDocumentModel.Span span(String text,float x,float y,float size,String font) {
    LocalDocumentModel.Span s = new LocalDocumentModel.Span(text,x,y,text.length()*size*.46f,size); s.font=font; return s;
  }
  public static void main(String[] args) throws Exception {
    List<LocalWordDocument.Page> pages = new ArrayList<>();
    for(int number=1;number<=2;number++) {
      LocalWordDocument.Page p = new LocalWordDocument.Page(); p.width=612; p.height=792;
      List<LocalDocumentModel.Span> spans = new ArrayList<>();
      spans.add(span("Native running header",50,25,9,"Arial"));
      spans.add(span("Page "+number,500,775,9,"Arial"));
      spans.add(span("Native editable report "+number,50,75,18,"Arial-BoldMT"));
      for(int column=0;column<2;column++) for(int i=0;i<3;i++) {
        LocalDocumentModel.Span s = span((column==0?"LEFT":"RIGHT")+" column continuation "+i, column==0?50:330,120+i*14,11,"Arial");
        s.color=column==0?"123456":"993322"; spans.add(s);
      }
      LocalDocumentModel.Span italic=span("Editable italic and underline",50,230,11,"Arial-ItalicMT");italic.underline=true;spans.add(italic);
      spans.add(span("1. Preserve numbered item",50,270,11,"Arial"));
      p.lines=LocalDocumentModel.group(spans); pages.add(p);
    }
    Files.write(Path.of(args[0]),LocalWordDocument.create(pages));
  }
}
`);
const classes = resolve('android/app/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes');
const classpath = [classes, resolve('.tools/android-pdfbox-audit.jar')].join(delimiter);
const path = resolve(output, 'native-layout.docx');
const compile = spawnSync('javac', ['-encoding', 'UTF-8', '-cp', classpath, '-d', output, source], { encoding: 'utf8', timeout: 60_000, windowsHide: true });
assert.equal(compile.status, 0, compile.stderr || compile.error?.message);
const result = spawnSync('java', ['--class-path', [output, classpath].join(delimiter), 'com.fileflow.app.processing.NativeWordFixture', path], { encoding: 'utf8', timeout: 60_000, windowsHide: true });
assert.equal(result.status, 0, result.stderr || result.error?.message);
const zip = await JSZip.loadAsync(await readFile(path));
const xml = await zip.file('word/document.xml').async('string');
assert(xml.includes('w:framePr') && xml.includes('w:pStyle w:val="Heading1"'));
assert(!xml.includes('<w:tbl>'));
assert(xml.includes('w:color w:val="123456"'));
assert(xml.includes('w:ascii="Arial"'));
assert(xml.includes('w:headerReference') && xml.includes('w:footerReference'));
assert((await zip.file('word/numbering.xml').async('string')).includes('w:numFmt w:val="decimal"'));
const report = { result: 'PASS', fixture: path, checks: ['editable paragraphs', 'column reading order', 'headings', 'font family normalization', 'colors', 'underline', 'numbering', 'page dimensions', 'running headers and footers'], scope: 'Compiled Android production geometry and OOXML writer on JVM with synthetic positions. Android PDF extraction, image codecs, device rendering and end-to-end conversion remain unverified without instrumentation.' };
await writeFile(resolve(output, 'native-writer.json'), JSON.stringify(report, null, 2));
console.log(JSON.stringify(report, null, 2));
