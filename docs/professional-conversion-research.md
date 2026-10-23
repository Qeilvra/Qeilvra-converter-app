**FileFlow conversion investigation — October 23, 2026 (Asia/Karachi).**

The verified improvements are editable ruled-table reconstruction and an explicitly selected, real office-rendering path for Word-to-PDF. General professional-quality conversion on Android is **not solved**. The supplied CV's PDF-to-Word rendering is unchanged from the implementation that was present when this task started. There is no paid production dependency, deployment, cloud-provider integration, or automatic document upload in this change.

The strongest free renderer successfully exercised here is **LibreOffice 26.8.1.1 for DOCX-to-PDF**, with substantial differences still visible on the underspecified supplied DOCX. For PDF-to-DOCX there is no universal winner: FileFlow renders the supplied CV, running-matter sample, typography sample, table/image sample and Word-exported report closer than the tested pdf2docx releases; pdf2docx renders the synthetic CV and columns closer and produces a good merged table. LibreOffice PDF import also generated editable text-box outputs, but lost actual table structure and rendered worse in Word. No cloud engine was measured: provider credentials were unavailable, and no account was created or personal document submitted to a third party.

**What was actually executed.**

The starting Git revision was `a5095dd8e7cbc91fc8a7f6df5998e38cde3049d4`. A complete copy of its five browser conversion modules was captured before edits under the ignored `web/.pdf-word-benchmark/professional/` directory. The two original files were located in `test/fileflow_conversion_test_files/`, not `tests/fixtures/`. They were copied to the ignored `.tools/professional-conversion/sources/`; SHA-256 checks verify that both originals are unchanged.

The suite includes the real PDF and supplied DOCX, plus a synthetic designed CV, independent columns, two-page prose with running matter, a ruled table with an independent image, mixed typography/Unicode, a merged/multiline table, and a DOCX authored with styles, margins, a merged table, an image, headers/footers and an explicit page break. The authored DOCX was rendered by Microsoft Word and its PDF was also used as an additional PDF-to-Word input. These are small fixtures, not a comprehensive document corpus.

Browser conversions ran in real Chromium with outside hosts and processing API requests blocked. DOCX results were opened and exported by the installed Microsoft Word in separate hidden instances with macros and link updates disabled. Comparisons use 108 dpi RGB pages, identical dimensions, no registration or resizing, mean absolute pixel error on the 0–255 scale, foreground intersection-over-union at a mean-RGB threshold of 220, normalized text sequence and character counts, page counts, and independent OOXML inspection. A lower pixel error is not a general conversion accuracy percentage. Character counts alone do not establish reading order or editability.

The genuine React Word-to-PDF button was separately tested against the repository's Java worker, bound only to `127.0.0.1:8091`, using the extracted local LibreOffice executable. Both the supplied CV and the authored report went through the actual checkbox, upload, conversion and download flow. Only loopback received document bytes. A failed worker request produced an error and no plain-text fallback. This local experiment was not a hosted deployment.

**Engine comparison.** “Documented” means a capability from vendor/source documentation; it does not mean its output was measured here. Android REST access is different from an embedded on-device SDK.

| Engine / API | PDF to Word quality | Word to PDF quality | Offline? | Android compatible? | Free usage | Actual test result |
|---|---|---|---|---|---|---|
| FileFlow browser: PDF.js + geometry + docx | Editable positioned paragraphs; improved ruled grids and rectangular merges; approximate fonts | Basic text export remains lossy; optional office renderer now connected | Yes for local tools | Browser only; native is separate Java | Existing permissive runtime dependencies | Real CV and representative PDFs converted and rendered; merged-table improvement measured |
| FileFlow native: PDFBox Android + Java OOXML | Same reconstruction strategy, separate implementation | Basic text export; installed-office handoff; optional configured service | Yes for local paths | Native Java/XML | Existing Apache-2.0 port and app code | 20 JVM tests; APK/lint; compiled grid writer rendered; full extraction/device UI unverified |
| pdf2docx 0.5.8 and 0.5.13 | Flowing editable reconstruction; sample-dependent fidelity | Not a DOCX renderer | Yes, desktop Python | No supported drop-in Java/Android or browser runtime | 0.5.8 metadata says GPL v3; 0.5.13 MIT; PyMuPDF dependency remains AGPL/commercial | Both versions generated outputs locally and were rendered in Word; measured outputs matched on the original seven PDF samples |
| PyMuPDF core | Geometry/rendering/extraction building blocks, not a ready PDF-to-DOCX writer | Office support is a separate commercial extension | Yes | Native MuPDF has Android bindings; Python package is not a native Java SDK | AGPL/commercial | Used as a development inspector and fixture renderer, not added to app |
| PyMuPDF Office 2.0 | Not evaluated as PDF-to-DOCX | Documented office layout engine | Yes; volume licenses can report usage | Documented desktop platforms, not Android | Restricted evaluation: first three pages; commercial production | Installed in isolated test directory; both local DOCX exports failed with SmartOffice `err=2`; alternate loader also failed; no valid PDF claimed |
| LibreOffice 26.8.1.1 | Editable text-box import; no actual recovered Word tables on this suite | Full office layout; much better suited than text reflow | Yes, desktop worker | Not a small Java SDK; Collabora supplies an Android office application | Free/open source, MPL-2.0 with component notices | Both DOCX inputs rendered; authored report retained two pages/all text; local UI integration passed. Eight PDF imports generated and were rendered; CV error 20.4517, merged-table error 5.1799 |
| Microsoft Word installed on this PC | PDF Reflow is documented but local importer did not return a document for the synthetic probe | Reference renderer used here | Local desktop export | Separate Android app, not an embeddable free SDK | Existing Office license; not a free dependency | Successfully opened/rendered DOCX results and saved an actual table-cell edit; PDF-import experiment explicitly failed |
| Adobe PDF Services | Documented PDF export to DOCX; no measured ranking | Documented Create PDF from Word | No | REST through protected credentials/backend | 500 document transactions/month; card-free signup | Research only; no credentials or provider upload |
| CloudConvert | Documented layout/table recovery | Documented Office-to-PDF | No | REST, not embedded | 10 credits/day; personal/testing/hobby tier | Research and read-only live pricing only; no conversion credentials |
| ConvertAPI | Documented flowing, continuous and exact layout options | Documented Office rendering | No | REST, not embedded | 250 card-free trial conversions; development/testing only | Research only; numeric paid pricing could not be verified through its protected page |
| Aspose.PDF Cloud + Aspose.Words Cloud | PDF Cloud exports PDF to Word | Use Words Cloud for DOCX rendering; do not assume PDF Cloud is the same engine | No | REST, not embedded | 150 free calls/month; trial/development terms apply | Research only; client ID/secret unavailable |
| PDF.co | No native PDF-to-DOCX endpoint found in its current published API index | Document-to-PDF endpoint documented | No | REST | Card-free, 30-day trial with 10,000 credits | Not shortlisted for PDF-to-Word; API output untested |
| Aspose.PDF FOSS for Java | Explicitly outside repository scope | DOCX input explicitly outside repository scope | Yes, for supported PDF operations | Java 11+ does not establish Android support | MIT | Excluded after source verification; no fabricated DOCX test |
| Apryse | Core PDF-to-Office requires Structured Output, documented for desktop/server | Office-to-PDF documented for Android and browser | Yes with licensed components/assets | Android Office-to-PDF yes; Core Structured Output is not documented as Android-supported | Evaluation, then commercial license | No trial key/Structured Output entitlement available; no output measured |
| Apache PDFBox / PDFBox Android | Extraction/graphics primitives; no professional DOCX reconstruction engine | No Word layout engine | Yes | Use Android port, not desktop PDFBox/AWT assumptions | Apache-2.0 | Existing native extraction dependency; native end-to-end accuracy requires a device |
| MuPDF.js / Apryse WebViewer WASM | MuPDF.js is not a ready editable-DOCX exporter; Apryse Core conversion capability must not be assumed for WebViewer | Apryse documents client-side Office rendering | Can be client-side with bundled assets | Browser technology, not a Java/XML office SDK | MuPDF.js AGPL/commercial; Apryse commercial | Documentation only; no permissive/free production Office renderer established by these options |

The [pdf2docx repository](https://github.com/ArtifexSoftware/pdf2docx) and [current release metadata](https://pypi.org/project/pdf2docx/) explicitly say Artifex no longer actively maintains it and that the current code is MIT licensed. That does not relicense the [PyMuPDF dependency](https://pymupdf.readthedocs.io/en/latest/license.html). The older installed 0.5.8 license was read from its local package metadata. No runtime dependency was upgraded. The [PDFBox Android repository](https://github.com/TomRoush/PdfBox-Android) identifies the separate Android port; a desktop Java library cannot be assumed to run without desktop classes on Android.

[LibreOffice's license page](https://www.libreoffice.org/licenses/) documents MPL-2.0 and component licensing. [Microsoft documents PDF opening/reconstruction](https://support.microsoft.com/en-us/word/opening-pdfs-in-word) and its layout limits; the installed Word importer failure in this experiment does not establish that Word generally lacks this feature. Its DOCX rendering was successfully used independently of PDF import.

Aspose's [FOSS Java repository scope](https://github.com/aspose-pdf-foss/Aspose.PDF-FOSS-for-Java#scope-and-limitations) explicitly excludes DOC/DOCX input and output. This contradicts a vendor blog's broad format table; the actual source scope is the reason it was excluded. The commercial enterprise product is a different offering.

[Apryse's Core documentation](https://docs.apryse.com/core/guides/features/office/convert-to-office/csharp) limits the Structured Output add-on to Windows, Linux and macOS. Its [Android Office-to-PDF API](https://docs.apryse.com/android/guides/features/office/apis) is separately documented. [WebViewer's browser engine](https://sdk.apryse.com/api/web/11.8/Core.html) and [MuPDF.js licensing](https://github.com/ArtifexSoftware/mupdf.js) establish browser/WASM options, not a free native Word renderer. [PyMuPDF Office's platform, evaluation and licensing documentation](https://pymupdf.readthedocs.io/en/latest/pymupdf-office/index.html) describes desktop support and the three-page restriction. Its inspected wrapper starts usage reporting for volume-license flags; this evaluation used no key and blocked Python socket connections. No commercial SDK is linked into FileFlow.

**Why professional engines differ, and what was applied.**

A PDF normally contains drawing instructions and positioned glyphs rather than the author's paragraphs, sections and table cells. [PDFBox's extraction FAQ](https://pdfbox.apache.org/2.0/faq.html) explains why stream order can differ from reading order. A converter needs a geometry/paint model, semantic grouping, table topology and a Word writer. A proper DOCX-to-PDF renderer additionally needs style inheritance, font resolution/metrics, shaping, line and page composition, tables, floating objects, sections, fields and running matter. PDF.js plus jsPDF text drawing does not provide that office layout engine.

Adobe's [Extract technical description](https://developer.adobe.com/document-services/docs/overview/) exposes contextual text blocks, typography, table cells/spans and reading order. That documents useful structural evidence; it does not disclose the proprietary Export-to-Word implementation or prove a particular algorithm is best. [ConvertAPI's public layout options](https://www.convertapi.com/pdf-to-docx) explicitly distinguish flowing text, continuous frames and exact text-box placement. pdf2docx provides an inspectable example of geometry/rule analysis followed by Word reconstruction. The applied improvement uses actual border topology rather than treating aligned text baselines as table rows.

Layout preservation works best for CVs, brochures, forms and fixed page compositions: keep page dimensions, measured positions, paragraph frames, source line breaks and independent images. It is editable, but longer edits can collide with neighboring frames. Flow reconstruction is better for long reports and prose: natural paragraphs, styles, sections, lists, tables and office-managed pagination. It can move content while making editing easier. Neither can infer design that has already been lost from the source. No second “Fully Editable” mode was added: the desktop Python alternative cannot run as the native Java engine, and a second native flowing mode has not been visually validated.

**Confirmed causes and implementation changes.**

The React button calls `localDocuments.pdfToDocxLocal`, which uses PDF.js viewport coordinates, paint metadata, `pdfWordLayout` and `pdfWordExport`; it does not call the optional pdf2docx server. Native `DocumentToolViewModel` independently calls `LocalDocumentProcessor.pdfToDocx`, PDFBox Android, `WordLayout` and `LocalWordDocument`. The previous layout improvements were connected to both PDF-to-Word buttons. Their UI descriptions were stale.

The supplied previous DOCX contains two text-bearing paragraphs, with almost the whole CV in one body paragraph. Its body run has no explicit font or size, and its default run/Normal styles also omit those values. It is therefore sensitive to the opening application's defaults. Its source history cannot be established from that file alone. The starting FileFlow implementation already produced four positioned paragraph groups from the provided PDF; it did not reproduce that two-paragraph output. This task does not attribute the earlier collapsed result to a currently executed path without evidence.

The provided PDF itself has one A4 page, no images, and only used 11-point regular black Helvetica spans. Spaced section labels are embedded in a uniformly styled text stream. There is no geometric CV column design or heading-size hierarchy left to recover. The available font resources include other families, but unused resources are not evidence that their styles occur on the page.

Its producer metadata is **jsPDF 4.2.1**. More decisively, its entire rendered page is pixel-for-pixel identical to the existing basic Word-to-PDF export of the supplied DOCX: RGB error **0.0000**, foreground overlap **1.0000**, and equal normalized text sequence. This supports the inference that the supplied “original” PDF is already a text-reflow derivative. Metadata and visual equality do not independently identify the generating application. An earlier designed CV cannot be reconstructed from layout information absent from both supplied artifacts. [The local provenance record](../.tools/professional-conversion/fixture-provenance.json) preserves the metadata, input hashes and measurements.

The actual Word-to-PDF buttons used entirely different, lossy paths: the browser extracted text and drew Helvetica 11 on fixed A4 pages with jsPDF; Android extracted text and used TextPaint/StaticLayout on fixed A4 pages. Those paths discarded Word styles, margins and page breaks. Unsupported tables/images were already rejected, which prevented some silent loss, but did not make simple styled DOCX rendering faithful.

Changes made:

- Browser `pdfWordLayout.ts` and native `WordGrid.java` recover closed ruled grids, join primitive cells across missing internal edges, accept rectangular merges, retain empty cells, and group multiple baselines within the correct cell. Grid dimensions are bounded. Existing unruled-table heuristics remain the fallback.
- Both Word writers emit real fixed-grid Word tables, column/row spans, multiline cell content, cell padding, row heights and original border styling. Actual Word rendering exposed floating-table left-margin offsets and shared row top-margin behavior; the writer was corrected and rerendered before accepting the change.
- `Times-Roman` now maps consistently to Times New Roman, including natively. No font binaries were embedded or paid fonts bundled.
- The browser Word-to-PDF screen exposes an unchecked office-service option only when `VITE_PROCESSING_API_URL` is configured. The normal local route stays available as **Export basic text PDF**. No selection, page load or service failure opts a file into upload or text fallback.
- Native Word-to-PDF uses the existing screen/spinner for an installed office app (default), explicit basic text export, and a configured-service choice when `PROCESSING_API_URL` exists. The office app handles its own export; FileFlow does not fabricate a result or history entry. English and Urdu descriptions explain these choices and their privacy boundaries.
- The existing Java LibreOffice worker is reused. Its engine probe incorrectly rejected an installed LibreOffice whose measured version startup took 8.0 seconds because probes allowed only five seconds. Probes now allow 15 seconds; the browser health check allows the three sequential bounded probes. Actual conversions through the React button passed afterward.
- PDF.js 6.4 returns field objects as a `Map`. The original `.size` guard was retained after checking the installed API; an initial object-key change was reverted. The new regression verifies the real button rejects form fields. This is not reported as a pre-existing bug fixed by this task.

**Measured PDF-to-Word results.**

These before/after values refer to the code at task start versus the final browser code, not the supplied historical DOCX. All eight samples retained source page counts and had zero missing/extra normalized rendered characters. Paragraph counts include text inside cells and running matter, exclude non-text anchors, and do not double-count OOXML alternate fallback representations of the same text box.

| PDF input | Text paragraphs before → after | Tables / editability | RGB error before → after | Foreground overlap before → after |
|---|---|---|---|---|
| Supplied CV | 4 → 4 | Positioned editable groups; no invented headings | 5.6830 → 5.6830 | .7028 → .7028 |
| Synthetic CV | 12 → 12 | Headings and lists retained | 1.9529 → 1.9529 | .6886 → .6886 |
| Two columns | 7 → 7 | Independent columns; no false table | 1.3038 → 1.3038 | .6853 → .6853 |
| Two-page prose | 14 → 14 | Sections, running headers/footers | .6620 / .6746 unchanged | .7775 / .7752 unchanged |
| Table and image | 12 → 12 | 1 editable table, 1 independent image | 1.2939 → 1.2939 | .8051 → .8051 |
| Typography/Unicode | 7 → 7 | Runs/colors/underline retained | .5316 → .5316 | .7402 → .7402 |
| Merged/multiline table | 8 → 9 | 0 → 1 actual table; 7 cells, 3-column merged heading | 2.2052 → 1.3425 | .5602 → .7013 |
| Report PDF exported by Word | 17 → 18 | Merged table recovered; 1 image, source rules represented as borders | 3.3405 → 2.7048; page 2 .6184 unchanged | .5437 → .6306; page 2 .6212 unchanged |

The merged-table RGB error fell **39.1%** on this fixture; the overlap improvement independently supports better placement. This is a measured improvement to one failure class, not a claim that all conversions improved by 39%. A separate compiled native writer produced the same editable table topology and a one-page Word rendering with zero missing/extra characters; its RGB error was 1.7299. That is a native writer/model test, not native Android PDF extraction or a native before/after accuracy comparison.

The Word-exported report provides an independently authored input beyond the PDF drawing fixtures. First-page error fell **19.0%**, with the second page unchanged. It retained normalized text sequence, two pages, one real image, and one actual table with a merged header. pdf2docx's first-page error was 6.1520 on that input and its extracted rendered text sequence differed, despite zero missing/extra character counts. This is why sequence and layout checks accompany character totals.

![Actual merged-table source, starting FileFlow output, and updated output](../.tools/professional-conversion/merged-table-before-after.png)

![Actual Word-exported report before and after the grid change](../.tools/professional-conversion/office-export-before-after.png)

For the supplied PDF, pdf2docx 0.5.8 and 0.5.13 produced three text paragraphs and RGB error **13.0825**, compared with FileFlow's four groups and **5.6830**. On the synthetic CV the direction reversed: pdf2docx error **.8754**, versus FileFlow **1.9529**. On the merged table, pdf2docx error **1.1384** was lower than FileFlow **1.3425**, while FileFlow had higher foreground overlap (.7013 versus .6845). These results support retaining the verified local implementation and borrowing structural techniques; they do not establish that pdf2docx or FileFlow is generally superior.

The supplied historical DOCX itself was freshly opened/rendered: two text-bearing paragraphs, RGB error **20.2425** and overlap **.0926** versus the supplied PDF. Current FileFlow gives four groups, **5.6830** and **.7028**, but that improvement already existed at task start. It is explicitly separated from this task's new table improvements in the comparison viewer.

LibreOffice's `writer_pdf_import` plus DOCX export initially failed while the system temporary drive was full. After redirecting temporary files to `D:`, all eight inputs converted successfully and were opened/rendered by Word. This was an environment failure, not evidence that LibreOffice lacks PDF import. The resulting documents used editable text boxes and retained rendered text/page counts, but recovered **zero actual Word tables**. Word-rendered error was 20.4517 on the CV, 5.1799 on the merged table, and 9.0192 / 1.9532 on the Word-exported report. That measured combination of weak semantic structure and poorer Word rendering is why it was not substituted for FileFlow's PDF-to-Word engine.

An actual Word edit replaced text in the second row's third cell, retained seven cells and one table, saved a separate DOCX and exported a new PDF. Whole pages were never inserted as images to impersonate editability. Vertical-merge topology/OOXML is unit tested; visual fidelity for arbitrary vertical merges, irregular table geometry and staggered cell content still needs a larger corpus.

**Measured Word-to-PDF results.**

The reference is Microsoft Word's rendering of each DOCX, rather than the original PDF from which an already damaged DOCX may have been created.

| DOCX input / path | Pages | Text preservation | RGB error versus Word | Foreground overlap |
|---|---|---|---|---|
| Supplied DOCX / existing basic exporter | 1 | Normalized sequence preserved | 20.2425 | .0926 |
| Supplied DOCX / actual button → local LibreOffice | 1 | Normalized sequence preserved | 15.6579 | .0879 |
| Authored report / existing basic exporter | Rejected | Table/images/headers require a full renderer | No PDF claimed | — |
| Authored report / actual button → local LibreOffice | 2 | Normalized sequence preserved; no missing/extra characters | .9265 / .2810 | .8823 / .8158 |

LibreOffice retained the authored report's merged table, image, running matter and explicit second page. The real UI conversions took about **50.8 and 56.7 seconds** on this host, including the request/download path; standalone conversions measured about 39–40 seconds. They are functional checks under development load, not controlled performance benchmarks. Browser PDF-to-Word samples took roughly 1.4–4.6 seconds in the final recorded runs.

The supplied DOCX remains visually poor relative to Word even through LibreOffice: pixel error decreases but foreground overlap does not improve. Its missing body/default font declarations are a concrete source of ambiguity. **Word-to-PDF fidelity for this CV is not declared fixed.** The existing basic exporter itself was not turned into an office renderer; it is now explicitly identified as a lossy option. An embedded native office renderer was not implemented.

**Cloud API evaluation and costs.**

No provider below received a personal or synthetic conversion file in this task. Synthetic experiments still require authenticated accounts; credentials/trial entitlements were unavailable. Vendor statements about speed and quality are unmeasured. No private API keys were added to React, an APK, Vercel configuration or committed source.

| Provider | Signup/free allowance | Limits, authentication and retention | Estimated cost for 1,000 short conversions |
|---|---|---|---|
| Adobe | 500 transactions/month, no card required | 100 MB, 25 requests/minute free tier; client ID/secret and token; assets default to 24 hours, with asset deletion available | A single month of 1,000 one-transaction jobs exceeds the quota; paid portion requires sales quote. 500/month over two months fits quota, subject to permitted production use |
| CloudConvert | 10 credits/day; card-free personal/testing/hobby signup | Free plan: 1 GB, 5 minutes, 5 concurrent tasks. Bearer key/OAuth. Jobs/exports deleted after 24 hours; explicit deletion supported | Minimum 4/2 credits per PDF→Office/Office→PDF job. At $0.017/credit: **$68 / $34** per 1,000 conversions. Subscription reference $0.009/credit: $36 / $18 of credits. Actual volume purchase/runtime vary |
| ConvertAPI | 250 conversions, card-free trial; testing/development only, not permanent production credits | Bearer token; published paid Developer limit 200 MB, larger paid tiers up to 2 GB. Trial size ceiling not independently established. In-memory response or optional stored download up to 3 hours | Current numeric paid prices could not be verified: the interactive price page presented bot verification. No historical dollar price is substituted |
| Aspose Cloud | 150 free calls/month; trial/development account. Card requirement for signup not independently confirmed | OAuth client ID/secret. PDF's last located size guidance is 100 MB (older support answer); Words has separate 1 GB guidance. Directly passed files are not retained after processing; Aspose Storage has a default **minimum 30 days**, unless explicitly removed/configured | Published first paid 1,000-call block is **$30** after the first 150 free calls. That is not necessarily 1,000 conversions: at three billable calls/document, the published tiers imply roughly **$196.50** for 3,000 calls. Verify workflow billing before adoption |
| PDF.co | Card-free, 30-day/10,000-credit development trial | `x-api-key`; DOCX-to-PDF costs 21 credits **per page**, plus upload/status calls. Plan page advertises 60-minute download links on smaller plans; that alone does not verify all retention. Current endpoint request-size ceiling was not established | 1,000 one-page conversions consume at least 21,000 credits. The advertised Personal plan has 37,000/month at $22.49/month equivalent **billed $269.89 annually**. That is an annual commitment, not a $22.49 one-off purchase; extra calls/pages change requirements |

Adobe's [pricing](https://developer.adobe.com/document-services/pricing/), [limits](https://developer.adobe.com/document-services/docs/overview/limits) and [security documentation](https://developer.adobe.com/document-services/docs/overview/security/) support its quota, card-free signup, limits and retention. Commercial distribution must be checked against its [developer terms](https://wwwimages2.adobe.com/content/dam/cc/en/legal/servicetou/Adobe-Developer-Additional-Terms_en-US_20230622.pdf) and [Acrobat Services terms](https://wwwimages2.adobe.com/content/dam/cc/en/legal/servicetou/Adobe-Acrobat-Services-Product-Specific-Terms-en_US-20241119.pdf); a free transaction quota is not proof that an externally distributed commercial app has all required permissions. FileFlow's eligibility was not established.

CloudConvert's [live pricing calculator](https://cloudconvert.com/pricing) was read in Chromium because static rendering omitted prices; the read-only capture is saved locally. [Conversion](https://cloudconvert.com/apis/pdf-to-office), [authentication](https://cloudconvert.com/docs/getting-started/introduction) and [retention](https://cloudconvert.com/docs/api-reference/jobs) have separate documentation. Estimates exclude free credits, tax and volume discounts. Its free plan is not treated as an established commercial production entitlement for FileFlow.

ConvertAPI's [getting-started guide](https://www.convertapi.com/docs/getting-started) supports the card-free 250-conversion offer. Its [current terms](https://www.convertapi.com/terms) restrict trial use to testing/development and describe paid plans/overages. [Layout and retention options](https://www.convertapi.com/pdf-to-docx) document the useful exact/flow distinction and optional short-lived storage. Paid commercial integration is offered under its agreement; no trial quota was treated as a permanent commercial allowance.

Aspose's [published pricing](https://purchase.aspose.cloud/cloud/pricing/), [trial scope](https://purchase.aspose.cloud/self-hosting/trial), [PDF-to-Word tutorial](https://tutorials.aspose.cloud/pdf/pdf-conversion/pdf-to-word/), [Words conversion documentation](https://docs.aspose.cloud/words/convert/) and [retention policy](https://about.aspose.cloud/legal/file-retention-policy/) support the distinctions above. The documented rate changes after the first paid block; multiplying a single low unit rate by every call would be misleading. Storage, download and cleanup requests may change total billable calls. The exact request-size limits and current signup/payment path need account-level verification before integration.

PDF.co's [API index](https://developer.pdf.co/llms.txt), [credit table](https://support.pdf.co/en/articles/11141295-how-many-credits-do-i-need), [trial guide](https://support.pdf.co/en/articles/11274235-how-to-sign-up-for-a-pdf-co-trial-account) and [pricing](https://www.pdf.co/pricing) support its documented conversions and trial/credit model. A support form's attachment limit is not treated as the API request limit. Production use requires an appropriate paid account/terms; its trial is not a free lifetime backend.

For any shared provider key, a backend/proxy must hold credentials, enforce quotas/size limits, avoid arbitrary URL fetching, delete provider assets and return the result. A public React bundle or distributable APK cannot keep a shared API secret. A free serverless quota does not remove bandwidth, abuse, duration or privacy constraints; none was deployed or assumed sufficient. A future cloud trial should start with this suite's synthetic inputs and record output renderings and real charges before selecting a provider.

**Verification, artifacts and production choice.**

- Web: 34 unit tests, TypeScript/production build, 38 real-browser tests across desktop and Pixel 5 browser emulation, and installed-PWA conversion with Chromium network disabled. The default local path made no processing API requests. Windows Playwright teardown required stopping its own Vite process after all 38 tests completed; final exit was successful.
- The actual PDF-to-Word button additionally downloaded DOCX files for the private CV, merged-table fixture and Word-exported report. Each matched the rendered engine sample's editable text and paragraph-frame geometry; both table inputs contained real merged tables. No uploads or external requests occurred.
- Service: 23 tests, successful package, and the real React-button/LibreOffice integration described above, including unchecked-by-default consent and failure-without-text-fallback checks.
- Android: 20 JVM tests, `assembleDebug`, `assembleDebugAndroidTest`, and lint passed (17 existing warnings). The final compiled native grid writer's DOCX was independently reopened and rendered in Word. ADB listed no connected devices: PDFBox extraction, Android office-app handoff, URI permissions, service choice and full real-CV conversion were not certified on a device.
- Word editability: a real seven-cell merged table was edited in Word, saved and rendered. Positioned paragraph editability is structural; large edits do not have a guaranteed natural reflow model.

Saved evidence is local and ignored by Git:

- [Complete comparison with actual source and rendered pages](../.tools/professional-conversion/comparison.html).
- [Machine-readable measurements](../.tools/professional-conversion/comparison.json), [quality gates](../.tools/professional-conversion/quality-gates.json), [original checksums](../.tools/professional-conversion/originals.json).
- [Current CV DOCX](../.tools/professional-conversion/browser-after/primary-cv.docx) and [Word rendering](../.tools/professional-conversion/browser-after/primary-cv.pdf).
- [Merged-table DOCX](../.tools/professional-conversion/browser-after/merged-table.docx), [actual edited copy](../.tools/professional-conversion/editability/merged-table-edited.docx), [edit verification](../.tools/professional-conversion/editability/verification.json).
- [Real office-button verification](../.tools/professional-conversion/office-button/verification.json), [authored report PDF through that button](../.tools/professional-conversion/office-button/office-report.pdf), and adjacent UI screenshots.
- [Real PDF-to-Word button verification](../.tools/professional-conversion/pdf-word-button/verification.json) and adjacent DOCX downloads/screenshots.
- [Native writer output](../.tools/professional-conversion/native-writer/merged-table.docx) and [rendering](../.tools/professional-conversion/native-writer/merged-table.pdf); [SDK evaluation failures](../.tools/professional-conversion/pymupdf-office/generation.json).

Recommended production architecture: keep PDF-to-Word local with measured reconstruction limits and no whole-page-image shortcut. For accurate free offline Word-to-PDF on Android, offer the installed office-app handoff and let a proper office application export the document. [Collabora documents offline mobile editing](https://www.collaboraonline.com/blog/collabora-office-4-2-0-for-ios-and-android/) and [PDF export](https://help.collaboraoffice.com/latest/en-US/text/shared/01/ref_pdf_export.html?DbPAR=SHARED); this handoff still needs device verification. A user-operated desktop LibreOffice companion is an optional alternative without a recurring hosting bill, though it requires that computer, a reachable secure connection and fonts. The repository's service route is now connected but remains opt-in and unconfigured in normal builds.

If seamless in-app, consistently high-fidelity conversion of complex documents is mandatory, the remaining choices are a substantial LibreOffice/Collabora native-core integration, a commercially licensed mobile office SDK, or a measured cloud conversion behind a protected backend. None is claimed free, integrated, licensed, or tested without evidence. General conversion accuracy, exact CV Word-to-PDF fidelity, large-file performance, arbitrary merged tables, RTL shaping, scans/OCR and native end-to-end behavior remain limitations. There is no basis to declare the whole issue fixed.

To repeat the local benchmark, use the existing development Python environment and installed Word. Set process `TEMP`/`TMP` to a workspace directory on `D:` on this host: the system drive ran out of space during evaluation. Only a verified synthetic rendering cache from this task was removed; unrelated user files were not cleaned. Repeating `setup` preserves an existing baseline snapshot.

```powershell
$env:TEMP = 'D:\pdf to excel\.tools\runtime-tmp'
$env:TMP = $env:TEMP
.tools/conversion-venv/Scripts/python.exe scripts/professional-conversion.py setup
node scripts/professional-conversion.mjs before
node scripts/professional-conversion.mjs after
.tools/conversion-venv/Scripts/python.exe scripts/professional-conversion.py pdf2docx
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/professional-word-render.ps1 -Phase browser-before
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/professional-word-render.ps1 -Phase browser-after
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/professional-word-render.ps1 -Phase pdf2docx
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/professional-word-render.ps1 -Phase word-reference
# Isolated latest pdf2docx test: set PYTHONPATH to .tools/office-evaluation/pdf2docx-latest.
# Compile Android, then run node scripts/verify-native-grid.mjs and render -Phase native-writer.
# Run the local Java/LibreOffice worker with an explicit executable path, loopback binding,
# CORS origin http://127.0.0.1:4183 and temporary directories inside this workspace.
node scripts/verify-office-renderer.mjs
node scripts/verify-pdf-word-buttons.mjs
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/professional-edit-check.ps1
.tools/conversion-venv/Scripts/python.exe scripts/professional-conversion.py inspect --assert
```

The LibreOffice executable used for evaluation is under `.tools/office-evaluation/libreoffice/SourceDir/LibreOffice/program/soffice.com`; it was extracted rather than system-installed. The downloaded MSI was removed after extraction. No emulator/device was available, no API account was registered, and no deployment or paid purchase was performed.
