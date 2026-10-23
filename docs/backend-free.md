# FileFlow backend-free investigation and implementation

Date: October 22, 2026, Asia/Karachi. This report supersedes backend-requirement statements in the previous audit, architecture, deployment and hosting documents. Those documents remain as the history and operating instructions for the optional service.

## Result and scope

All existing tool screens now use local code. They require no conversion server, Supabase Edge Function, paid API, login, database, card or monthly processing subscription. The web app must initially load its static assets; the installed PWA can then work offline while its browser cache is retained. Android is native Java/XML and does not load the website. Existing design, navigation, image/scanner tools, PDF editing, safety checks, exports and local history remain.

The repository's Spring Boot service, adapters, API clients and tests are retained. There is no automatic remote fallback: documents do not leave the device when a local conversion fails. An operator may still run the original worker and explicitly call its endpoints. The default screens do not expose that upload workflow.

This is editable reconstruction, not an exact PDF-to-Word layout engine. Complex Word-to-PDF rendering also remains outside the lightweight implementation. These limits are shown before processing and with results. Native implementation/build evidence is distinguished below from device execution: no connected Android device was available.

## Existing implementation inspected

Reviewed React routes/components, authentication, file sniffing and ZIP validation, browser image/canvas/scanner processors, PDF page operations, IndexedDB retention, downloads/sharing, API timeouts/format validation and browser/unit tests. Reviewed native activities/XML, ViewModels, SAF input/save handling, FileProvider sharing, PDFBox Android, OOXML/quoted TSV writers, bitmap/compression/scanner code, manifest, Gradle dependencies and instrumentation/JVM tests. Reviewed Spring controllers, validators, subprocess/workspace management, capability probes, table extraction, image recompression, Office adapters, synthetic benchmarks and prior audit records.

Previously required service calls:

| Flow | Previous dependency and reason | Local replacement |
| --- | --- | --- |
| PDF to Word | Spring Office adapter invokes Python/pdf2docx; not present in Vercel's static app | PDF.js positions + `docx` in web; Android PDFBox glyphs + native ZIP/XML |
| Word to PDF | LibreOffice executable owns pagination/font rendering | Explicit simple-text reflow only; complex content rejected |
| PDF to Excel analysis | Spring PDFBox/Tabula reconstructs tables | Positional text grouping and conservative column estimates on both clients |
| Excel export | Spring/POI on web; native ZIP/XML already local | Lazy ExcelJS on web; existing native text-first XLSX writer reused |
| PDF compression | Spring PDFBox/optional qpdf | Browser object-stream optimization; Android bitmap recompression with PDFBox Android |

The missing `/api/v1` route was an architecture dependency, not an authentication or Vercel database problem. Current screens contain no service health checks or conversion POSTs. The read-only browser tool could not re-open the public Vercel URL in this session, so the earlier live 404 observations are historical evidence, not a new production test. Production has not been redeployed.

## Every advertised feature

| Feature | Browser | Native Android | Limits and verification boundary |
| --- | --- | --- | --- |
| PDF to Word | New local PDF.js + DOCX writer | New PDFBox Android positions + OOXML package | Editable text, approximate sizes/style, page sizes, simple tables, bitmap images. Complex layout needs correction. Browser output independently reopened. Native device run pending. |
| PDF to Excel | New local extraction, existing cell editor, new local ExcelJS export | New local extraction feeds existing quoted-TSV editor and XLSX exporter | Repeated aligned multi-column rows; all inferred cells need review. Multiple tables/sheets; identifiers remain text. |
| Word to PDF | New jsPDF text reflow | New native PdfDocument/StaticLayout text reflow | A4, basic pagination. Browser supports basic Latin only and rejects other scripts; native uses device fonts. Tables, images, fields, notes and headers rejected. Full rendering is not claimed. |
| Image to PDF | Existing canvas/jsPDF | Existing native bitmap/PDF pipeline | Existing page arrangement, EXIF, margins and limits retained. |
| Image conversion | Existing JPG/PNG/WebP encoders | Existing native bitmap encoders | Real format signatures; JPEG white background; browser unsupported encoders already rejected. |
| Image compression | Existing quality/dimension search | Existing native search | Measured byte result; existing target-missed warnings retained. |
| PDF compression | New lossless structural re-save | New opaque-image JPEG recompression and structural re-save | Browser does not recompress images. Native can reduce image detail, skips masks/transparency/huge images. Original retained if not smaller; signed PDFs rejected. |
| PDF merge/split/rotation | Existing pdf-lib + ZIP | Existing PDFBox Android | Local; inherited native page resources and existing page/size guards retained. |
| Document scanning | Existing camera/import, corners, perspective and enhancement | Existing camera intent and native image pipeline | Image scanning already offline. Scanning an image is separate from recognizing its text. |
| Prepare for Upload | Existing image resizing/format/byte-target workflow | Existing native image workflow | Existing dimension validation and orientation fixes retained. Portal presets remain editable starting points. |
| File viewing | Existing image/page previews; downloaded files open in a compatible browser/OS app | Existing image/page controls; SAF save/share opens compatible installed apps | No new full Word/Excel viewer is claimed. Viewing Office files locally depends on a compatible installed reader. |
| Local history | Existing localStorage + optional IndexedDB outputs | Existing SharedPreferences metadata | No database. Browser output retention remains opt-in; native results live in ViewModels until cleared. |

## PDF-to-Word quality

The 2026-10-23 implementation and measured CV results are documented in [PDF-to-Word layout verification](pdf-to-word-accuracy.md). It adds geometric paragraph/heading/list/column reconstruction, real running matter, positioned editable paragraphs/tables/images, paint/underline recovery and font limitation reports in both platform-specific writers. The comparison and limitations below describe the earlier baseline unless superseded by that report.

`web/src/lib/localDocuments.ts` extracts PDF.js text transforms, groups baselines, infers separate cells from large horizontal gaps and requires repeated multi-cell rows to infer a table. Source font names are requested from PDF.js; familiar font families, bold/italic flags and point sizes become editable Word runs. Each source page creates a section with source dimensions and estimated margins. Bitmap image operators are exported as separate PNG assets and inserted in approximate reading order. Whole PDF pages are not substituted for editable content.

Android's `LocalDocumentModel` reads `TextPosition` glyph coordinates using the Android-specific PDFBox port. `LocalDocumentProcessor` coalesces glyphs into styled runs, writes actual `w:tbl` cells and packages individual image assets captured by a graphics stream engine. It avoids Apache POI, AWT, desktop Python, JNI document suites and a WebView for ordinary text-based conversions.

Limits: each recovered line is a separate paragraph; semantic paragraph/list/header/footer recovery is not solved. Source font binaries are not embedded. Vector drawings, page layers, clipping/compositing, precise image rotation/placement, complex columns, merged/multiline cells, bidirectional reading order and exact pagination are not guaranteed. Inferred tables can include multi-column prose. Native rotated pages and browser rotated/vertical text fail explicitly. Cropped/rotated layouts and unusual font encodings still need a representative corpus. Browser scans fail rather than producing image-only DOCX files. Native OCR is an explicit build/runtime option described below.

Actual same-input comparison was run using `scripts/compare-local-docx.py` against the repository's optional installed pdf2docx engine:

| Same two-page synthetic source | Browser local | Optional desktop pdf2docx |
| --- | --- | --- |
| Editable normal paragraphs | 6 | 7 |
| Word tables | 2 | 2 |
| Inline bitmap images | 2 | 2 |
| Sections | 2 | 2 |
| Leading-zero and 20-digit IDs present | Yes | Yes |
| Editable prose present | Yes | Yes |

These counts demonstrate content/structure, not equivalent appearance. The fixture includes headings, different page sizes, ordinary prose, table rows, images and vector artwork. The local output deliberately does not reproduce the vector rectangle. Neither engine's counts establish complex-layout fidelity. The desktop engine remains a development comparison only and retains its transitive licensing constraints; it is not an Android runtime candidate.

Independent `python-docx` and PyMuPDF inspection verified local DOCX paragraphs, both tables, both images, both source page dimensions, an 18-point bold heading and exact identifier/currency strings. No Microsoft Word/LibreOffice rendered comparison was possible; no visual accuracy percentage is invented.

## PDF-to-Excel data integrity

Only detected repeated multi-column rows become tables. Vertical gaps separate tables; pages remain separate. Missing cells are mapped to inferred column anchors and are editable. All inferred cells are flagged for review. Single-column rows, captions and unrecognized structures are not silently represented as complete table recovery: the UI states that only detected multi-column rows are included and asks users to compare the source.

Web values default to strings, including `001234`, `12345678901234567890`, `$1,234.50`, `03/04/2026`, `2026-10-22` and `=SUM(A1:A2)`. Formula-looking strings remain literal text. No locale/date/currency inference is applied. Explicit Number selection rejects leading zeros, grouping/currency syntax, nonfinite values and values with more than 15 digit characters. Explicit Date selection requires a real YYYY-MM-DD date in the supported Excel range. The first row stays text. Editing or changing types invalidates the prior result.

Native retains its existing quoted TSV format, explicit table markers and separate worksheets. Every value remains inline-string text. Native numeric/date selectors are not introduced; users can intentionally convert types later in their spreadsheet editor. This protects source values and avoids hidden interpretation. Preview edits now also invalidate native outputs and survive activity recreation in the ViewModel.

Entirely scanned browser PDFs fail with an OCR message. Mixed PDFs show the exact pages with no selectable text; users must recover those pages separately. Native defaults reject any page without selectable text rather than silently exporting a partial workbook. If optional OCR is enabled, scanned pages go through recognition and the same correction workflow. OCR can misread digits even though the XLSX writer preserves the recognized strings exactly. Interactive PDF form fields are rejected by editable conversion so their values cannot silently disappear. A regression caught PDF.js 6 returning form fields as a Map; the guard now checks its size.

## Offline OCR and application size

The default Android build omits OCR binaries/models. Build with `-PfileflowOfflineOcr=true` to bundle Google's ML Kit Latin model, then explicitly enable the checkbox in PDF-to-Word or PDF-to-Excel. Recognition runs on the device; it does not require a runtime model download, cloud conversion endpoint or website. The official Android guide documents bundled versus dynamically downloaded models: [ML Kit Android setup](https://developers.google.com/ml-kit/vision/text-recognition/v2/android).

Scanned pages are rasterized locally at up to 2× scale and 4 million output pixels; oversized source bitmap dimensions are rejected before rendering. Recognition uses a 60-second wait per page, at most 10 scanned pages per job, and requires manual review. A timeout does not forcibly terminate an SDK native call; bitmap cleanup waits for an in-flight task to complete. Already searchable pages use their source text. For Word, OCR creates editable text and omits the full-page scan bitmap, avoiding a duplicate image-only representation. Scan illustrations and original formatting are not reconstructed in OCR mode.

This model recognizes Latin scripts, not Urdu. Unreadable scans return errors. OCR instrumentation is supplied but has not been executed on a device; quality, runtime SDK behavior in airplane mode, memory and speed remain unverified. ML Kit is distributed under Google's SDK/model terms, not an Apache/MIT open-source model license. Existing Google advertising SDKs remain unchanged and may make network/diagnostic requests; document-local processing does not imply that all SDK telemetry disappears.

The universal debug APK with the bundled OCR model measured approximately 65.5 MB, versus approximately 23.9 MB before that dependency. The default build therefore excludes it. A release Android App Bundle can deliver ABI-specific libraries, but no release installed-size measurement is claimed. The build option lets users choose offline OCR without forcing its size onto every installation.

Browser OCR was investigated, not bundled. Tesseract.js needs self-hosted worker/core WASM and trained language data to be truly offline; its defaults can use CDN downloads. It does not accept PDFs directly, so a PDF renderer and bounded rasterization are also needed. Adding these large assets to every PWA install is outside the lightweight default. No CDN OCR path or paid OCR substitute was introduced. [Tesseract local installation](https://github.com/naptha/tesseract.js/blob/master/docs/local-installation.md), [PDF support limitation](https://github.com/naptha/tesseract.js/blob/master/docs/faq.md).

## Alternatives and licensing decisions

| Candidate | License / platform considerations | Decision |
| --- | --- | --- |
| PDF.js | Apache-2.0; browser PDF parsing/text/images, not a Word semantic converter | Selected; worker hosted and cached with the site. [API](https://mozilla.github.io/pdf.js/api/draft/api.js.html). |
| docx | MIT; writes actual editable OOXML tables/runs/images; does not infer PDF semantics | Selected, lazy-loaded. [Image API](https://docx.js.org/api/classes/index.ImageRun.html). |
| ExcelJS | MIT; already installed for fixtures; valid string/number/date XLSX writer | Promoted to runtime, lazy-loaded; no second spreadsheet engine added. |
| SheetJS CE | Apache-2.0; explicit text cell types can preserve IDs | Feasible alternative; no duplicate dependency. [Cell types](https://docs.sheetjs.com/docs/csf/cell/). |
| PDFBox Android 2.0.27.0 | Apache-2.0; existing Android port, not desktop PDFBox/Tabula on Android | Reused; dependency is based on an older PDFBox core and needs continuing security/version review. [Project](https://github.com/TomRoush/PdfBox-Android). |
| Native ZIP/XML + PdfDocument | Existing project writer + Android platform APIs; minimal additional dependency | Selected native Office packaging/basic text PDF output. |
| MuPDF WASM / PyMuPDF | AGPL or commercial licensing; parsing/rendering does not automatically solve editable Word semantics | Evaluated; not bundled. [WASM license](https://github.com/ArtifexSoftware/mupdf/blob/master/platform/wasm/README.md). |
| PDFium/WASM | BSD-style upstream plus third-party notices; PDF rendering/text extraction, not DOCX reconstruction | Feasible alternative renderer, but duplicates PDF.js/Android PDF parsing and adds WASM/native assets. [License](https://github.com/chromium/pdfium/blob/main/LICENSE). |
| LibreOfficeKit / LibreOffice WASM, ZetaOffice | LibreOffice licenses and many native/WASM/font assets; full office engine integration has substantial app/memory/testing cost | Technically feasible richer offline Word renderer; not represented as impossible. Not integrated into this lightweight patch. [ZetaOffice](https://zetaoffice.net/), [ZetaJS source](https://github.com/allotropia/zetajs). Wrapper MIT licensing alone does not cover the whole engine. |
| Desktop POI, Tabula, pdf2docx | Existing desktop dependencies; Android lacks desktop AWT/process/runtime assumptions; POI does not itself lay out Word pages | Kept in optional server, not copied unchanged into Android. |
| ML Kit bundled Latin OCR | Google SDK/model terms; additional app binaries; no cloud recognition request | Optional native build, explicit runtime checkbox; device verification pending. |
| Tesseract / native Tesseract ports | Apache-2.0 engine with language-model and native ABI assets; supports additional languages such as Urdu with appropriate trained data | Possible alternative to ML Kit, but no second OCR runtime/model added. Need model quality and device benchmarks first. |
| Commercial conversion/AI services | Server access, keys, credits or licenses | Not introduced. |

## Limits, packaging and error behavior

New conversion paths use 20 MB source and 100-page limits. Browser table review is capped at 10,000 cells; export at 100,000 cells, 100 tables, 32,767 characters per cell and a 2-million-character total. Browser DOCX image dimensions and cumulative pixels are preflighted before decoding; image bytes and page asset counts are bounded. PDF.js runs in its own worker and is destroyed/cleaned after each job, with a 120-second worker deadline. Grouping and Office serialization still perform some work on the browser main thread; per-page yields improve responsiveness but do not establish constant memory or a hard CPU deadline for every parser operation.

Native work remains on existing background executors and reports page progress. PDFBox uses temporary-file scratch storage, but source/result buffers and bitmaps still consume memory. Out-of-memory errors become explicit tool errors where catchable; Android process eviction is not preventable by a Java catch. No measured peak-memory or large-file throughput guarantee is made. Existing larger page-editing/merge limits are unchanged. Password-protected inputs are rejected.

Browser PDF optimization preserves text and bitmap data through pdf-lib object streams; savings can be zero. Native compression visits nested image resources with cycle/depth/deadline guards, skips giant/transparent/masked assets and retains only a smaller candidate. Both report actual output sizes. PDF rewriting invalidates digital signatures; signed inputs are rejected by the new compressors. Browser and native error UI does not invent a successful download.

The production PWA caches `.mjs` as well as `.js`, including the same-origin PDF.js worker. Build output precaches approximately 5,060 KiB, including direct document-engine license notices. PDF.js, docx and ExcelJS are dynamically imported; their approximate gzip chunks are 131 KiB, 168 KiB and 271 KiB, plus the 1.26 MB uncompressed worker. Precache deliberately downloads offline tools during installation; lazy imports reduce execution/startup loading, not total installed cache size. Older Safari/Firefox and low-memory mobile browsers remain a test gap; current Chromium desktop/mobile emulation is verified. Regenerate resolved direct-engine notices with `node scripts/collect-document-licenses.mjs`; this is not a complete transitive SBOM.

## Verification and remaining work

Repeatable checks:

```text
npm --prefix web run typecheck
npm --prefix web test -- --run
npm --prefix web run test:e2e
npm --prefix web run build
node scripts/verify-offline-web.mjs
node scripts/verify-native-xlsx.mjs
.tools/conversion-venv/Scripts/python.exe scripts/verify-local-documents.py
.tools/conversion-venv/Scripts/python.exe scripts/compare-local-docx.py
android/gradlew.bat --offline testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug
android/gradlew.bat -PfileflowOfflineOcr=true testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug
```

The Python checks use the already-installed optional development benchmark environment; Python is not part of either app runtime. Browser tests create genuine PDFs/DOCX/XLSX and reopen downloads; they abort every `/api/v1` request. The production PWA test disables networking after installation and performs both Word and Excel conversion, including worker loading and downloads. The native pure-Java XLSX exporter is independently reopened by ExcelJS.

Reviewable generated files are under `.tools/backend-free-evidence/chromium-desktop/`: `source.pdf`, `editable.docx`, `tables.xlsx`, `independent-inspection.json`, `optional-pdf2docx.docx` and `engine-comparison.json`. Mobile browser evidence is in the neighboring `chromium-mobile` directory. `offline-web.json` records the production offline check. These are ignored local build/benchmark artifacts, not user documents.

Native instrumentation covers local text/table/image recovery, scan rejection, OCR editable-text recovery, compression/text retention and the previous image/PDF regression suite. It requires a real device/emulator. ADB started successfully with SDK access but listed no devices; the only installed system-image directory was incomplete. Native source/test compilation is not conversion-accuracy evidence.

Still needed: execute native instrumentation and SAF/camera/share/lifecycle checks on representative API 24–36 devices, airplane-mode OCR with the bundled model, peak-memory/latency measurements, realistic maximum-size/complex/RTL corpora, and Word/Excel visual inspection. A larger document engine or broader OCR language package remains a separate engineering integration rather than a claimed solved feature. No infrastructure, paid service, production deployment, signed release or user-file upload was performed.

## Final execution addendum

- Web TypeScript/production build: passed on the final source. PWA precache contains 34 entries, approximately 5,060.28 KiB.
- Web unit regressions: 6 files, 25 tests passed, zero failures.
- Full desktop/mobile browser regression run: 32 tests passed. After adding the final form/signature guards, the eight-test local-conversion subset passed on both profiles, including the two new platform-specific safety cases. These are overlapping runs, not 40 distinct tests.
- Final production offline check: passed with Chromium networking disabled after PWA installation. Deep links, the PDF.js worker, extraction, DOCX, XLSX and actual downloads worked without any `/api/v1` request.
- Independent DOCX inspection: passed; six normal editable paragraphs, two tables, two bitmap images and two source-sized sections, with identifiers/currency and an 18-point bold heading verified. Independent native XLSX/ExcelJS inspection also passed for two sheets, leading zeros, long IDs, formula-like text and embedded tabs/newlines.
- Both Android build settings passed `testDebugUnitTest`, `assembleDebug`, `assembleDebugAndroidTest` and `lintDebug`. JVM results: 14 tests, zero failures/errors. Lint: zero errors, 17 warnings. Instrumentation was compiled and packaged, not executed.
- Default debug APK: **23,924,548 bytes**, `android/app/build/outputs/apk/debug/app-debug.apk`; generated `BUNDLED_OFFLINE_OCR` is false. Optional OCR debug APK: **65,516,333 bytes**, preserved at `.tools/backend-free-evidence/app-with-offline-ocr-debug.apk`, with its matching test APK beside it. These are debug builds, not signed production releases.
- `git diff --check` passed. No backend source files were changed or removed.

Verification caught and corrected actual integration issues: Android-port method signatures, default-hidden PDF.js font metadata, PDF.js 6 Map-based form metadata, missing native Word table-grid markup, and the missing Urdu OCR option translation. Earlier browser runs also exceeded test time budgets when Android compilation, bundling and browser conversions competed for resources; the final conversion subset was run without simultaneous builds and passed. No intermediate failure is counted as a successful conversion or device test.
