# Web audit and repairs — 2026-10-22

Scope: existing React/TypeScript client, browser-local engines, processing API contract, Vite/PWA and Vercel configuration. Existing branding, routes and optional Supabase work were preserved. No production deployment or credentials were changed. No new paid service or dependency was added.

## Confirmed issues and implementation

| Feature | Confirmed cause | Repair and files | Verification / result |
| --- | --- | --- | --- |
| Remote conversions on Vercel | API defaults to same-origin `/api/v1`; the repository had no production API proxy and the user confirmed no backend is deployed | `web/.env.example` explains the separate HTTPS Spring worker and build-time URL; `web/vite.config.ts` proxies only local development | Production conversion remains **blocked** until a real worker is hosted and the frontend is rebuilt with its URL. Local source configuration repaired; no fabricated backend |
| Direct navigation | React routes lacked Vercel SPA routing | `web/vercel.json` rewrites application paths, excludes `/api`; PWA excludes `/api` from navigation fallback | Source fixed; redeployed Vercel behavior remains unverified |
| Service health | Service strips always displayed a green dot | `ProcessingServiceStatus.tsx` calls `/health/capabilities`, checks the specific operation, provides retry and unavailable state | Client contract/unit and browser checks; actual engine health owned by backend checks |
| Invalid/empty server outputs | Any successful HTTP body was accepted as a document | `lib/processingApi.ts` parses PDF outputs, checks required DOCX/XLSX ZIP entries, rejects empty outputs and HTML fallback; `ResultCard.tsx` handles failed save initiation | Malformed/empty response unit tests, browser HTML/404 tests; fixed |
| Hung service requests | Upload and export requests had no timeout; cold backend dependency probes exceeded the original 10-second health deadline | Shared request helper aborts at 150 seconds through body consumption; health uses 20 seconds; network/413/429/503/404 errors actionable | Timeout and missing endpoint unit tests; fixed |
| Compression claims | PDF reduction trusted a response header | Compare actual output size with original; image compressor warns when JPEG re-encoding is not smaller | Valid PDF parse and misleading-header unit test; fixed |
| Concurrent actions | Upload/settings/export controls remained active during processing | Shared `ToolLayout.tsx` fieldset disables controls while reading or processing | Browser workflows; fixed |
| Stale downloads | Edits to image format, target, table cells or PDF pages left previous results available under current settings | All relevant tool screens invalidate results/previews after edits; invalid selections clear prior results | Browser changes image formats and edits exported table cells; fixed |
| Converter navigation | PDF→Word and Word→PDF reused a stateful component | `ToolRouter.tsx` keys converters and image/scanner components by tool ID | Typecheck and browser routing; fixed |
| Excel types | UI had no explicit conservative type controls | `types.ts` and `PdfToExcel.tsx` offer Text, Number and ISO Date per column; text is default; first row remains headers | Browser fixture contract preserves `001234` and `12345678901234567890`; actual server export verified separately |
| Image encoder fallback | Browsers can fall back to PNG for unsupported formats, while filename retained requested format | `lib/images.ts` checks encoded MIME and nonzero size | Browser JPG/PNG/WebP signatures, decoded dimensions, alpha/white background checks; fixed |
| Invalid image requirements | Prepare for Upload allowed zero/negative/nonfinite targets/dimensions into canvas processing | Shared image compressor validates 10 KB–10 MB target, integer dimensions 1–8192 | Shared guards implemented; browser happy paths and typecheck; full format/size matrix remains unverified |
| Fit-to-image PDF | Margins could exceed tiny fitted page dimensions, producing negative image placement | Fit page includes margins; PDF dimensions capped within engine limit; margins validated | Browser one-pixel input produces a parseable PDF whose page includes both margins; fixed |
| Scanner geometry | Crossed corners accepted; width/height caps independently distorted aspect ratio | `lib/scanner.ts` rejects crossed/degenerate geometry; uses one capped scale; adds 24 MP limit | Geometry unit test and real browser-created parseable scan PDF; mathematical quality across real camera samples remains unverified |
| Scanner responsiveness/resource use | Multi-file scans decoded all images concurrently; edge-detection retry had an unhandled rejection; corner handles required pointer input | `ImageToPdf.tsx` detects sequentially, cleans failed preview URLs, catches retry errors and supports arrow keys for corners | Browser scanner workflow; reduced concurrent decoding by construction, no peak-memory benchmark |
| Excessive local PDF workload | Page editing and merge lacked a combined page limit | `lib/pdf.ts` enforces 500-page local editing/merge limits and validates selected page numbers | 501-page rejection and invalid page selection tests; fixed |
| Recent activity | Storage quota errors could surface as conversion failures; retained files outlived the 30-record limit; Clear raced pending retention | `lib/files.ts` isolates metadata failures, prunes retained files, serializes writes/clearing and invalidates pending generations; malformed dates rejected | Quota unit test; browser retention, Save again, 35-output prune to 30, immediate Clear of pending write and IndexedDB count zero; fixed |
| Privacy copy | Home claimed all files stayed local; service copy promised deletion; privacy omitted optional retained outputs | `Home.tsx`, `InfoPage.tsx`, `StatusBanner.tsx`, Settings copy explain remote uploads, attempted cleanup and optional IndexedDB retention | Source review; fixed |
| Settings recovery | Storage errors and sign-out failures could become uncaught promise/event failures | `SettingsPage.tsx` reports errors and permits retry | Typecheck/unit settings workflows; live Supabase sign-out not tested |
| Response memory use | Successful body downloads, JSON and Office XML decompression were unbounded in the client | `lib/processingApi.ts` caps streamed output at 100 MiB, table JSON at 8 MiB, health at 128 KiB, error JSON at 256 KiB and required ZIP XML at 20 MiB per entry | Oversized Content-Length rejection and actual PDF/XLSX validation tests; fixed, exhaustive archive fuzzing unverified |
| Unused processing engines | Legacy browser table-extraction/export functions were unreachable from the product but pulled ExcelJS and PDF.js worker assets into builds | Removed dead code in `lib/pdf.ts` and unused worker declaration; removed `pdfjs-dist`, moved test-only `docx`/`exceljs` to development dependencies; lock refreshed offline | Search of call sites and production build; final asset sizes below |

Issue accounting: this table groups related findings; do not sum this table with root/backend/Android counts without deduplicating shared API, data-integrity and deployment issues.

## Execution evidence

- Baseline `npm.cmd run typecheck`: passed. Baseline Vitest: **15 tests / 4 files passed**.
- Post-repair TypeScript strict check: passed. Vitest: **23 tests / 6 files passed** (includes API body/health validation, timeout, output PDF parsing, storage quota, geometry, invalid pages and 501-page limits).
- Production build passed; Vite emitted production assets and PWA worker. One observed build took 70 seconds under concurrent audit workloads; this is not evidence of a speed improvement.
- Playwright: **20 tests passed** across desktop Chromium and Pixel 5 emulation before the additional 320 px/theme sweep. Includes actual browser-local image conversion, PDF creation/edit/split, output parsing, image format/alpha validation, optional local retention and service error contract tests.
- Additional 320 px/theme sweep and final rerun: see final root report for latest result.
- No dedicated lint configuration or script existed. Strict TypeScript checking ran; it is not a substitute claim that ESLint passed. No lint dependency was added merely for the audit.
- `npm.ps1` is disabled by local PowerShell policy; commands use `npm.cmd`. An attempted E2E command at repository root failed because that package has no `test:e2e` script, then was run from `web/`.

## Evidence boundaries

Playwright processing endpoints are explicitly fixture stubs. Those tests verify review UI, request/response handling and downloads, **not PDF-to-Word reconstruction, LibreOffice fidelity, real Tabula extraction or server PDF compression quality**. Browser-local tools execute real canvas/PDF code. PDF/ZIP output structure is parsed; no claim is made that Office desktop applications were launched.

Not completed here: real camera capture/permission testing, physical Android behavior, Safari/Firefox, live Supabase authentication, a full 30–50 MB input matrix, exhaustive malformed-file fuzzing, complex Unicode/layout/OCR conversion fidelity, production CORS/HTTPS/reverse-proxy deployment, or comparative runtime/memory measurements. Scanned-PDF OCR is not implemented.

## Deployment requirements

Deploy the existing worker separately with its runtime engines; set the exact web origin in backend CORS; expose HTTPS; set `VITE_PROCESSING_API_URL=https://<worker-host>/api/v1` in Vercel before rebuilding. With Vercel project Root Directory `web`, the included Vercel config serves SPA deep links without swallowing API routes. Local Vite defaults to `http://127.0.0.1:8080` via proxy. No database/account is needed solely for document conversions. Do not place private API secrets in `VITE_*` values.
