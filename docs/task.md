# Delivery and verification status

## Implemented

- Hybrid web/Android/backend architecture with truthful local versus service messaging.
- Layout-aware PDF-to-DOCX service adapter; removed browser/Android paragraph-dump conversions.
- LibreOffice headless DOCX-to-PDF with an isolated profile and PDF parser validation.
- PDF image downsampling/recompression at low, balanced, and high levels, optional qpdf optimization, and no false reduction result.
- Tabula ruled/stream table extraction, editable web preview, uncertainty flags, multiple XLSX sheets, and typed numeric cells.
- Web scanner with camera/import, automatic edges, draggable four-corner correction, projective warp, filters, brightness/contrast/sharpening, multipage reorder/delete/rotate/export.
- Android scanner with capture/import, automatic corners, manual four-coordinate editing, perspective correction, filters, and multipage management.
- Backend file/page/time/concurrency/export limits, per-request workspaces and cleanup, non-shell engine calls, parser validation, security headers, exact CORS, and hardened container configuration.
- Existing image tools, PDF merge/split/rotate/reorder, recent metadata, download, sharing, navigation, and responsive routes retained.

## Verification record — 2026-10-17

- Backend Maven: 5/5 tests passed. Tests cover real PDF parsing, invalid-file rejection, image-heavy PDF reduction with selectable text retained, ruled-table extraction to typed XLSX, and the complex English/Urdu DOCX fixture.
- Web Vitest: 13/13 passed.
- Web TypeScript and production/PWA build: passed; 31 assets precached.
- Web Playwright: 10/10 desktop/mobile scenarios passed after the scanner compatibility fallback.
- Android `testDebugUnitTest`: passed and all modified Java/resources compiled with SDK 36.
- Docker build/runtime: not executed because the installed Docker CLI could not reach a running Linux daemon.
- Physical Android camera, save/share sheet, and low-memory behavior: not run; no device/emulator was connected.
- LibreOffice and `pdf2docx` end-to-end conversions: not run locally because their binaries are provided by the unstarted container.
- Professional converter visual benchmark: not run; representative licensed corpus and reference applications were unavailable.

## Release blockers

- Run the target Docker image and full engine integration suite.
- Complete the ten-document visual/editability benchmark and record measured scores.
- Select and license a maintained commercial PDF-to-DOCX engine if the unmaintained MIT baseline misses the agreed fidelity gate.
- Add approved OCR/table recognition for scanned PDFs; current behavior detects the scan and refuses to fabricate data.
- Verify Android on physical camera/storage/share targets.
