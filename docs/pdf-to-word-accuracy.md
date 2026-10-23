# PDF-to-Word layout verification — 2026-10-23

Follow-up: the [professional conversion investigation](professional-conversion-research.md) adds verified ruled merged-table reconstruction and explicit office-renderer choices. The measurements below describe the preceding reconstruction work, not improvements newly attributed to that follow-up.

FileFlow now reconstructs editable paragraphs, evidenced headings, lists, reading regions, running matter, simple tables, bitmap images and rules locally. The browser and native Android implementations have separate layout/writer changes. No application UI, branding, navigation, backend requirement, paid dependency or deployment was introduced.

The primary files are `test/fileflow_conversion_test_files/Muhammad_Dawood (1).pdf` and `Muhammad_Dawood.docx`. They were read locally; the supplied files were not rewritten. Generated documents, source copies and images remain in the ignored `.tools/pdf-word-quality/` directory. Do not commit the private `test/` inputs or generated CV evidence.

**What the supplied files establish.** The supplied DOCX has two text-bearing paragraphs and two runs: “Page 1” and essentially the entire CV. The title run specifies 13 points, bold and blue; the body run does not specify a font family or point size. It contains no tables, images or semantic heading styles. Its Word rendering is one page.

The supplied PDF itself is already flattened: one A4 page, exclusively 11-point regular black Helvetica, a “Page 1” line, then 27 uniformly spaced body lines at one left coordinate. Section labels appear inside those body lines. It contains no font-size/weight hierarchy or geometric columns from which a designed CV can be recovered. The converter therefore does not invent headings for this particular file. The four output blocks are bounded editable groups where semantic paragraph boundaries are uncertain, not a claim to have recovered the original author's paragraphs.

The starting repository engine produces **28** text-bearing paragraphs from that same PDF, unlike the supplied DOCX's **two**. Thus the supplied collapsed output is not reproduced by the starting engine. An earlier or separate export path may have produced it; that provenance is unverified. The two baselines are kept distinct throughout the evidence.

**Before and after, using the supplied DOCX as “before.”** Counts exclude empty section/image anchors and include text in table cells and running matter where applicable.

| Measurement | Supplied DOCX | Improved browser DOCX |
| --- | --- | --- |
| Paragraph count | 2; one contains the CV body | 4 bounded editable text groups |
| Editable text preservation | Editable; normalized text sequence matches the PDF | Editable; normalized text sequence matches the PDF |
| Font preservation | Unspecified run family/body size; title changed to 13-point bold blue | Explicit 11-point regular black Arial; Helvetica substitution recorded |
| Heading reconstruction | No semantic heading styles; unsupported title formatting | No invented hierarchy; source has no visual heading evidence |
| Page count, rendered by Microsoft Word | 1 | 1 |
| Text positioning | Reflowed body | Page-relative paragraph frames and original line breaks |
| Mean absolute pixel RGB error, 0–255; lower is better | 20.2425 | 5.6830 |
| Foreground intersection-over-union; higher is better | 0.0926 | 0.7028 |
| Missing / extra non-space rendered characters | 0 / 0 | 0 / 0 |

Pixel metrics compare 108-dpi RGB renderings with no alignment correction or resizing. Foreground is mean RGB below 220. These are measurements for these pages, not a general accuracy percentage. Arial/Helvetica outlines, office text rasterization and small font-metric differences remain visible in the heatmaps.

**Confirmed source-code causes and repairs.**

The previous browser writer emitted each PDF line as a paragraph. Its font mapper reduced almost every actual family to Arial unless the name literally contained “serif” or “mono”; for example, a real Times family could lose its serif identity. Colors and underlines were absent from the text model. Spacing was derived from adjacent global baselines, including unrelated columns. Consecutive lines with large horizontal gaps became tables without evidence distinguishing prose columns. Images lost their x coordinate and flowed inline, displacing surrounding text. Native Android had its own per-line paragraph writer, approximate margins/line spacing, equally broad table inference and inline image placement; a browser-only fix would not repair it.

- `web/src/lib/pdfWordLayout.ts`: weighted body-font estimation; continuation grouping; gap, indentation, size and weight boundaries; conservative heading styles; persistent gutters including staggered baselines; column reading order; short/numeric aligned table evidence or actual rules; bounded uniform-text fallback; repeated edge matter detection. Excel keeps its existing detector.
- `web/src/lib/pdfWordPaint.ts`: recover glyph paint colors from the PDF operator stream, including multiple colors merged into one PDF.js TextItem; restore repeated spaces present in glyph operators; infer underlines from nearby rules; retain simple horizontal/vertical rules; record unmatched paint and unsupported vector paths.
- `web/src/lib/pdfWordExport.ts`: editable Word paragraph frames, hard line breaks within paragraphs, evidenced heading styles, list numbering, measured marker indentation, page-sized sections, real headers/footers, positioned tables and floating image anchors. Table margins, row geometry and source rule colors/thickness are explicit. Font normalization/substitution and limitations are written to `FileFlowConversionReport` in DOCX custom properties.
- `web/src/lib/localDocuments.ts`: connect extraction to the new Word model/writer; preserve image x/y coordinates, crop/viewport geometry and reject unsupported rotated/mirrored images. Existing resource limits, scan/form guards, worker cleanup, Excel export and compression behavior remain in place.
- `android/.../LocalDocumentModel.java`: optional native text-color extraction for Word. Excel calls retain the previous extraction mode.
- `android/.../WordLayout.java`: independent native paragraph, heading, list, gutter/column, running-matter and table analysis.
- `android/.../LocalWordDocument.java`: native ZIP/XML packaging for positioned editable paragraphs, fonts/styles/colors/underlines, actual numbering, tables, sections, headers/footers, anchored images and conversion limitations.
- `android/.../LocalDocumentProcessor.java`: collect native geometry and graphics, detect source underlines/rules, package independent rule assets, and call the native writer. Word-to-PDF and Android file handling were not changed.

Paragraph frames were chosen over one textbox per PDF line: each detected paragraph remains an ordinary editable `w:p` with `w:framePr`; related source lines share a paragraph. Images and simple rules are independent assets, never whole-page substitutes for selectable text. Fixed placement improves initial fidelity but substantial editing can expand frames into adjacent content; the result is not guaranteed to reflow like an authored Word template.

**Same-input engine regression results.** “Before” below is revision `dc6a8bd4aea5db405225e7d2fb225f0880c6362e`, captured by the harness from Git, rather than the supplied DOCX.

| Source | Text paragraphs before → after | Other verified change | RGB error before → after |
| --- | --- | --- | --- |
| Primary CV | 28 → 4 | 1 rendered page, original normalized text sequence preserved | 18.7817 → 5.6830 |
| Synthetic CV | 16 → 12 | Title plus 4 section headings; wrapped bullet continuation grouped | 7.3209 → 1.9529 |
| Two-column prose | 13 → 7 | False table removed; independent column reading order | 5.2285 → 1.3038 |
| Multipage prose | 18 → 14 | 2 pages, real running headers/footers, numbered items | 1.1503 / 1.1830 → 0.6620 / 0.6746 |
| Ruled table and image | 12 → 12 | 1 editable table, 1 independently positioned image | 7.4334 → 1.2939 |
| Typography and Unicode | 7 → 7 | Bold/italic, colors, underline, Latin/Greek/Cyrillic text, heading styles | 2.4986 → 0.5316 |

All six samples retain source page counts and have zero missing/extra non-space rendered characters. The report also records normalized sequence equality, OOXML text counts, font families, sizes, colors, list/style counts and matched-line coordinate errors. Character histograms alone cannot establish reading order; sequence and geometric inspection complement them. No RTL-layout accuracy or scanned-text accuracy is inferred from the Unicode sample.

**Libraries and runtime architecture.** Existing PDF.js `6.4.299`, pdf-lib `1.17.1` and docx `9.13.0` are reused in the browser. Existing PDFBox Android `2.0.27.0` plus Java ZIP/XML and Android bitmap APIs are reused natively. There are no new runtime dependencies or conversion services. Existing library notices/licenses continue to apply: PDF.js and PDFBox Android are Apache-2.0; pdf-lib and docx are MIT. The older Android PDFBox port's maintenance/security limitations remain an existing dependency consideration. No alternative library was integrated. PyMuPDF, numpy and python-docx in the existing development environment and the installed Microsoft Word renderer are test-only tools, not app dependencies. Their development-environment licenses do not become browser/Android runtime dependencies. Existing optional backend code is untouched.

**Verification.**

- TypeScript and production PWA build passed. Precache: 36 entries, about 5,000.91 KiB. The new reconstruction modules are lazy loaded with the existing conversion path.
- Web unit suite: 33 tests passed. Geometry regressions include continuation groups, independent/staggered columns, numeric tables, lists, repeated edge matter, uniform-text limits and font normalization.
- Full browser suite: 36 tests passed across Chromium desktop and Pixel 5 browser emulation. Existing Excel, Word-to-PDF, compression, image, scanner, PDF editing, storage, theme and navigation checks passed. New real-PDF checks cover mixed colors in one line, serif substitution, underline, paragraph grouping and semantic numbering. Mobile browser emulation is not Android-native verification.
- Production offline PWA check passed with Chromium networking disabled after installation: PDF.js worker, Word conversion, Excel conversion and downloads all worked with zero processing API requests.
- Quality gates independently inspect actual DOCX XML, render with Microsoft Word, check rendering input hashes, compare source/rendered page counts and character counts, and require lower measured image error for every sample.
- Android default build passed `testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug`: 19 JVM tests, zero failures/errors. Instrumentation code was compiled and packaged. Lint reported zero errors and 17 pre-existing warnings.
- The actual compiled native Java model/writer generated a two-page DOCX on the JVM, independently reopened and rendered by Microsoft Word. This checks native packaging/layout output from synthetic positions, not Android PDF extraction. The native XLSX writer also passed independent ExcelJS reopening, including exact identifiers and embedded tabs/newlines.
- ADB started and listed **no connected devices**. Native PDFBox extraction accuracy, full CV conversion on Android, native graphics codecs, OCR and device performance remain unverified. No Android-native conversion accuracy claim is made. Execute `connectedDebugAndroidTest` with a device/emulator before signing off native accuracy. The optional OCR build/model was not changed or certified by this task.

The final local Chromium timing run measured about **0.758 s** for the primary CV, versus **0.480 s** for the baseline engine in its recorded run. The six improved conversions ranged approximately **0.695–1.394 s**. The largest observed 16-ms interval stall was about 88 ms, and about 23 ms for the CV. These small fixtures, cache states and concurrent development workloads are not a controlled throughput benchmark. `after/browser.json` retains the raw interval observations; peak memory, large PDFs and low-memory devices were not measured. Additional reconstruction costs CPU time; no speedup is claimed.

**Remaining limits.** Uniform source text cannot reveal lost authoring semantics. Font binaries are not embedded; standard PDF fonts use recorded substitutions, browser-unavailable families receive recorded fallbacks, and native custom families may be substituted by the opening office application. Complex vectors/fills, clipping, compositing, arbitrary image transforms, merged/multiline table cells, overlapping content and RTL reading order need further work. Table and heading inference is conservative and can still be wrong on unfamiliar layouts. Numbered PDF items retain their original labels with separate numbering definitions; extensive list editing may require renumbering. Rules are represented as small independent image assets when not already represented as underlines/table borders. Browser scans still require offline OCR that is not bundled; no image-only DOCX shortcut was added.

**Saved evidence and repeatable commands.**

- `.tools/pdf-word-quality/comparison.html`: local slider review of original PDF versus improved/baseline/supplied Word renderings.
- `.tools/pdf-word-quality/after/primary-cv.docx`: improved editable CV; adjacent `.pdf` is Microsoft Word's rendering.
- `.tools/pdf-word-quality/before/`: Git-baseline DOCX files/renderings and `supplied-cv.docx`/`.pdf`.
- `.tools/pdf-word-quality/images/<sample>/<before|after|supplied>/`: source, Word, overlay and difference PNGs for each page.
- `.tools/pdf-word-quality/comparison.json`, `quality-gates.json`, phase-specific `browser.json` and `word-render.json`: machine-readable metrics, source/render hashes and timings.
- `.tools/pdf-word-quality/android/`: actual native-writer DOCX/PDF, page PNGs and independent inspection reports.

Run from the repository root, using the existing local Python environment and installed Microsoft Word:

```powershell
.tools/conversion-venv/Scripts/python.exe scripts/pdf-word-fixtures.py
# Keep the desired baseline revision explicit after committing these changes.
$env:FILEFLOW_BASELINE_REF = 'dc6a8bd4aea5db405225e7d2fb225f0880c6362e'
node scripts/pdf-word-quality.mjs before
node scripts/pdf-word-quality.mjs after
powershell -NoProfile -File scripts/render-word-quality.ps1 -Phase before
powershell -NoProfile -File scripts/render-word-quality.ps1 -Phase after
.tools/conversion-venv/Scripts/python.exe scripts/inspect-word-quality.py --assert
npm --prefix web test -- --run
npm --prefix web run test:e2e -- --workers=1
npm --prefix web run build
node scripts/verify-offline-web.mjs
# After compiling Android:
node scripts/verify-native-docx.mjs
powershell -NoProfile -File scripts/render-word-quality.ps1 -Phase android
```

The fixture generator currently uses the installed Windows Arial font files; these are development fixtures, not bundled app assets. The harness accepts an optional local PDF path as its third argument; source copies remain under `.tools`. It records the selected baseline Git revision, source checksums and remote/API request attempts. On this Windows host the first Playwright-owned Vite server needed explicit cleanup after all tests passed; the final 36-test run used a directly managed local Vite process and exited successfully in 2.4 minutes.

Other changed support files: `.gitignore` excludes generated baseline modules; `web/src/lib/pdfWordLayout.test.ts`, `web/e2e/pdf-word-layout.spec.ts`, `android/.../WordLayoutTest.java` and `android/.../LocalProcessingTest.java` provide regressions; `scripts/pdf-word-fixtures.py`, `pdf-word-quality.mjs`, `render-word-quality.ps1`, `inspect-word-quality.py` and `verify-native-docx.mjs` generate and independently inspect the evidence. `scripts/verify-local-documents.py` and `compare-local-docx.py` count anchored as well as inline images so the earlier independent checks remain meaningful. `test-fixtures/README.md` and `docs/backend-free.md` link this current suite/result.
