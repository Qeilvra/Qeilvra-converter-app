# Native Android audit and repair — 2026-10-22

The Android product remains Java/XML with its existing screens, branding and navigation. It has no WebView and does not require browser deployment, a database or Supabase login. The Google Ads/UMP SDKs remain in the project with test ad IDs. No new infrastructure or paid dependency was added.

The merged release manifest was inspected, including dependency-added permissions. Besides internet/network state, the existing SDKs contribute advertising ID, AdServices ID/attribution/topics, wake-lock and foreground-service permissions; AndroidX also contributes its app-scoped non-exported dynamic-receiver permission. There is no broad storage permission or direct camera permission: document access uses SAF and camera capture delegates to a camera app. Advertising-related permissions must be included in release privacy and consent review; document-local processing does not remove them.

## Actual processing architecture

| Feature | Implementation and network requirement | Output and current limitations |
| --- | --- | --- |
| PDF to Word | HTTPS Spring Boot `/api/v1/convert/pdf-to-docx` | DOCX package validated before success; engine fidelity and OCR depend on the backend. No image-only substitute is used. |
| Word to PDF | HTTPS `/api/v1/convert/docx-to-pdf` | PDF reparsed and page count checked before success. LibreOffice is required on the backend. |
| PDF to Excel | HTTPS `/api/v1/pdf/tables`, editable native TSV, local OpenXML writer | Separate tables become separate worksheets. All values are text, protecting leading zeros and long identifiers. There are no native number/date column controls yet. Merged cells, cross-page table stitching and OCR remain extraction-engine limitations. |
| PDF compression | HTTPS `/api/v1/pdf/compress` | Requires both a positive reduction response header and fewer actual output bytes; otherwise reports that no safe reduction was found. |
| Image to PDF | Native bitmap processing and `PdfDocument` | Local multipage PDF with sequential image decoding; large batches can still exhaust native memory. |
| Image conversion | Native JPG/PNG/WebP codecs | Local; EXIF orientation is applied, JPEG transparency is replaced with white, PNG retains alpha. The converter limits output to a 4096 px long edge. |
| Image compression | Shared bitmap renderer and local JPEG quality/size search | Local, measured output size; a missed target is explicitly reported. Output is JPEG. |
| Scanner | External camera intent, edge estimate, manual four-corner correction, perspective warp, grayscale/threshold and PDF creation | Local. Edge estimation is heuristic, not a reliable automatic crop guarantee; review corners. Camera capture, real-world detection and low-memory batches need device verification. |
| PDF editor | PDFBox Android with temporary-file scratch storage | Local merge, reorder/delete/rotate and split-to-ZIP. Merged output is capped at 500 pages. |
| Prepare for Upload | Shared local compressor and image-edit pipeline | Respects both EXIF and user rotation, aspect ratio, maximum dimensions and actual byte targets. Presets are convenience defaults, not a promise of acceptance by a destination portal. |
| Recent activity | App-private SharedPreferences | Last 30 metadata entries only; not an output file library. |
| Settings | AppCompat theme/locales; metadata clearing; UMP privacy options | Theme preference now survives process restart. |
| Save/share | Storage Access Framework and restricted FileProvider paths | Provider writes and share-cache creation run on background workers. Unique share files prevent later exports overwriting a pending share. |

For cost planning, the image tools, scanner, image PDF creation, PDF editing and XLSX packaging are already fully on-device. PDF/Word rendering and table detection require the server in the current implementation. PDF compression is a candidate for a future on-device PDFBox implementation, provided image quality, memory limits and inherited resources are verified. Local OCR could be optional later, but no OCR provider, paid API or new native OCR dependency was introduced in this repair. High-fidelity editable Office conversion has no verified native replacement in this repository.

## Confirmed issues repaired

These are 20 repair groups; test and device status below qualify claims about execution.

| ID | Confirmed problem | Repair and primary files | Verification / result |
| --- | --- | --- | --- |
| A01 | A backend origin without `/api/v1` produces wrong conversion paths. | `ProcessingEndpoint.java`, `ProcessingApiClient.java`: normalize origins and validate documented API base paths. | URL regression tests; source repaired. Live hosted backend is a separate deployment dependency. |
| A02 | String-prefix URL checks accepted emulator-host lookalikes; HTTP exception applied to release builds. | Strict URI validation, debug-only exact `10.0.2.2` exception, release cleartext disabled, debug network policy restricted to that host. | URL rejection tests; device transport test pending. |
| A03 | HTTP upload buffering and unbounded bodies could allocate excessive memory; redirects could upload documents to another endpoint. | Chunked uploads, bounded streaming and error bodies, redirects disabled. | `BoundedStreams` regression tests; network interruption on device unverified. |
| A04 | Nonempty 2xx HTML or unrelated binary responses were accepted as generated documents. | Reparse PDF results; require a bounded, readable DOCX ZIP with expected package entries. | Package rejection tests; backend/device integration still required. |
| A05 | Native compression trusted a header without checking actual byte reduction. | Compare output bytes against bytes uploaded; no output success if unchanged/larger. | Source inspection; device-to-worker integration unverified. |
| A06 | Missing or dishonest provider size metadata bypassed limits; partial header reads could reject valid files. | Validate actual stream size and read the full signature; sum actual document sizes for combined limit. | Source review plus bounded-stream/signature tests; cloud provider checks require device. |
| A07 | XLSX export had no input/cell bounds and silently discarded invalid XML control characters. | Explicit export limits and rejection of unsupported characters; preserve valid Unicode and carriage returns. | Actual generated workbook ZIP/XML regression tests. |
| A08 | Extraction replaced embedded tabs/newlines with spaces and collapsed all tables into one worksheet. | Reversible quoted TSV, escaped quotes and visible table boundary records; separate worksheet package relationships. | Round-trip multiline/cell/worksheet tests. |
| A09 | Native XLSX data-integrity behavior lacked coverage. | Keep inline text cells; tests protect `001234`, 20-digit IDs, Unicode and formula-like values. | Actual generated worksheet content tests. |
| A10 | Provider validation, scanner corner detection, full image preview decoding and output writes ran on the UI thread. | `ToolIo.java` background workers; bounded thumbnails; background SAF and share writes. | Source verification; responsiveness and timing on devices unmeasured. |
| A11 | Selecting or editing during conversion could race shared mutable state; retries left stale downloadable outputs. | Disable relevant controls during work; clear old results on a new attempt; reject duplicate processing; preserve locks after activity recreation. | Source review; rotation/in-flight instrumentation remains necessary. |
| A12 | Page edits invalidated bytes but left stale result controls visible. | Hide result actions immediately when pages change. | Source review; device UI validation pending. |
| A13 | Prepare for Upload could exceed requested dimensions after EXIF rotation and ignored its manual rotate control. | Shared render pipeline applies EXIF/user edits before fitting the final width/height bounds. | Native EXIF/rotation regression instrumentation added; execution needs device. |
| A14 | Separate image code omitted EXIF transpose/transverse cases and duplicated orientation handling. | Shared renderer now supports all mirrored rotation cases; compressor reuses that renderer. | Source verification; full EXIF fixture matrix still requires device. |
| A15 | Intermediate compression bitmaps leaked on exceptions; very large decode surfaces had no pixel cap. | Finally-based bitmap recycling and a decoded-surface cap before allocation. | Source review; no measured memory benchmark or maximum-batch guarantee. |
| A16 | Crossed/non-finite scanner corner coordinates could produce unusable or blank perspective output. | Validate convex clockwise coordinates; reject invalid manual corners; fall back to reviewable defaults for invalid automatic estimates. | Pure geometry tests; real camera-image fidelity unverified. |
| A17 | PDF merge did not enforce the total page cap; zero-page documents could proceed; page editing omitted inherited page-tree resources and could lose fonts/images. | Validate page counts and explicitly preserve inherited resources when importing edited pages. The omission was confirmed against installed PDFBox Android bytecode, which explicitly warns callers to copy these resources. | Native PDF create/merge/edit/split and inherited-font instrumentation added; execution needs device. |
| A18 | Saves used non-truncating provider mode; process death after CreateDocument could silently leave empty output; repeated sharing overwrote earlier pending shares. | Truncating asynchronous writes, explicit missing-result error and best-effort removal of newly created empty/partial destinations, unique shared files, ClipData read grants. | Source review; SAF provider/storage/share validation needs device. |
| A19 | Theme changes were not persisted; SDK 36 screen layouts lacked system-bar/cutout insets; delayed ad callback could attach after destruction. | Persist/restore night mode; apply insets to each activity; check destroyed state before ad attachment. | Java/resource build checks; visual/device verification pending. |
| A20 | English/Urdu privacy text promised deletion and claimed generated copies were never retained; an unused text-only PDF renderer remained in production. | Describe cleanup attempts and share/camera cache retention accurately; remove unused `AndroidDocumentProcessor`. | All production references checked; no private user data deleted. |

## Verification ledger

- Baseline command: `gradlew.bat --offline testDebugUnitTest assembleDebug lintDebug --console=plain`. It succeeded in 1m 48s, but Java compilation and the old five unit tests were **up-to-date**, not a fresh source verification.
- Initial post-change compilations exposed four source errors, which were repaired. A subsequent compiler failure was an environment `AccessDeniedException` when the reused Gradle daemon accessed the installed Android SDK's `android.jar`. A fresh `--no-daemon` build with approved SDK access progressed through actual Java/test compilation.
- Fresh JVM results: 14 tests, 0 failures, 0 errors, 0 skipped (`FileValidatorTest`: 3; `OfficeOpenXmlTest`: 6; `ProcessingSafetyTest`: 5), executed 2026-10-22. These inspect actual generated workbook XML/package contents and conservative text values, quoted preview round trips, separate worksheets, invalid output packages, URL validation, bounded streams and scanner geometry. Final build/lint/release outcomes are recorded below; the initial cached baseline is not substituted for those outcomes.
- Independent workbook-reader verification also passed: `node scripts/verify-native-xlsx.mjs` compiled the production Java exporter, generated an XLSX, and reopened it with ExcelJS. It verified two sheets, `001234`, a 20-digit identifier, formula-like literal text, and embedded tab/newline characters. This verifies actual native-exporter output without claiming that device UI was exercised.
- Existing `MainNavigationTest` covers dashboard navigation. New `LocalProcessingTest` uses real native image codecs, EXIF orientation, PDF generation, merge, page reorder/delete/rotation and split PDF parsing. Instrumentation source compilation alone is not execution evidence.
- ADB failed to start its daemon on both port 5037 and an isolated 5038 with the writable Android user home. Camera, SAF providers, device sharing, real offline behavior, rotation/background transitions, TalkBack, low-storage recovery and physical-device compatibility remain unverified.
- Lint baseline included dependency/SDK update warnings and trust-manager warnings inside the PDFBox BouncyCastle dependency. The application HTTP client does not install those dependency trust managers. These findings need dependency compatibility review; no vulnerability status is inferred from lint alone.
- No measured startup, processing-time, memory, or throughput speedup is claimed. Resource limits, background I/O and explicit cleanup were verified by code/test checks where stated.

## Deployment and release requirements

1. Host the existing Spring Boot worker separately from the Vercel web frontend, including its conversion engines. Configure the native build with `-PfileflowProcessingApiUrl=https://your-worker.example/api/v1` or `FILEFLOW_PROCESSING_API_URL`. The origin alone is also normalized. No shared backend secret should be embedded in the public APK.
2. Local image/scanner/PDF-editing tools remain usable without that URL or an account. Server tools fail clearly when it is absent. Native conversion has no local editable PDF-to-Word engine and no OCR fallback.
3. Provision signing outside source control through the four existing `FILEFLOW_KEYSTORE_PATH`, `FILEFLOW_KEYSTORE_PASSWORD`, `FILEFLOW_KEY_ALIAS`, and `FILEFLOW_KEY_PASSWORD` variables. A successful unsigned release assembly does not produce a Google Play ready release.
4. Configure real ad IDs and validate consent/privacy behavior before public release. Review Google Ads SDK data collection in the privacy policy and store Data safety declaration. The source's document-local processing claim does not mean the ad SDK sends no device data.
5. Validate current Google Play compatibility/signing/target requirements against official policy at release time, install the signed build on representative API 24 through current devices, and run the instrumentation and manual matrix above. Android SDK 36 is the repository's present compile/target configuration, not evidence of store acceptance.
6. Generated results remain in memory until their screen's ViewModel is cleared. Background/rotation generally retain work; OS process death does not recover generated bytes or pending camera state. Large image PDF batches and large merged outputs still need low-memory device measurement before the advertised maximums can be certified.
7. The native editable table view uses quoted TSV rather than the web cell grid. Keep visible `=== FileFlow table N ===` lines to preserve separate worksheets. Embedded tabs/newlines are quoted and doubled quotes represent a literal quote. Native number/date column choices and the web grid's uncertainty highlighting remain parity gaps; default text export is deliberate data protection.

## Final execution results

Pending completion of the current fresh Gradle verification run. This section must be updated before the audit is signed off.
