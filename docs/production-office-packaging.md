# LibreOffice production packaging — 23 October 2026

The packaging repair is implemented and tested against the built static application. **The live Vercel website is unchanged and is not claimed fixed.** No deployment or Git push was performed. Public redistribution also has an unfinished matching-source/license package, described below.

## Confirmed cause

The old installation populated `web/public/office-runtime/`, which is deliberately ignored by Git. `npm run build` only ran TypeScript and Vite; it never ran the explicit `setup:office` command. A clean Vercel checkout therefore had the renderer integration but none of its engine assets. The old setup also depended on scripts and notices above the Vercel project root.

Read-only Vercel CLI inspection confirmed the actual project `qeilvra-converter-app` uses root directory `web`, framework Vite and Node 24. No project settings were changed. [Vercel's root-directory documentation](https://vercel.com/docs/builds/configure-a-build#root-directory) explains why production preparation must not depend on parent-directory files.

Read-only requests to the public site confirmed:

| URL | Actual response before this change |
| --- | --- |
| `/tools/word-to-pdf` | HTTP 200, real SPA HTML |
| `/office-renderer/index.html` | HTTP 200, real renderer HTML |
| `/office-runtime/manifest.json` | HTTP 200, **SPA HTML**, not JSON |
| `/office-runtime/soffice.wasm` | HTTP 200, **SPA HTML**, not WASM |

The broad SPA rewrite concealed missing files behind HTTP 200. COOP/COEP headers were already present on the live site, so missing cross-origin isolation was not the observed cause. The old CacheFirst runtime policy could retain the bogus HTML manifest.

The live site was also tested in an isolated Chromium session through its real selection and conversion button using the synthetic report. It reproduced the exact original “not packaged” error and no Save button. Non-GET requests were blocked, no document was uploaded, and the runtime manifest again returned HTTP 200 HTML. Before screenshots and the request record were saved.

## Implemented packaging

`web/package.json` now runs mandatory runtime preparation, TypeScript, Vite, then output verification. Missing downloads, changed upstream content, missing build assets, bad hashes or oversized static output fail the build. There is no environment switch that silently omits the engine.

The canonical installer, lock file and notices now live inside `web/`. The root setup command remains a compatibility entry point. On a cold build the installer downloads public engine software only. It verifies the original byte lengths and SHA-256 digests, then losslessly compresses WASM and data. It caches verified originals under `web/node_modules/.cache/fileflow-office/`. Generated public/runtime and dist files remain out of Git.

Compressed files use `.gzip`, with `Content-Type: application/gzip` and **no manually configured Content-Encoding**. Vite's static server automatically decodes `.gz` responses; an actual production-browser test caught that double-decoding problem during implementation. Using ordinary `.gzip` payloads removes that dependency on server encoding behavior. The browser verifies compressed digests, decompresses with `DecompressionStream`, verifies original digests, and supplies `Module.wasmBinary` and `Module.getPreloadedPackage` to the unchanged engine.

All engine files and the application UNO worker share a content-versioned directory. The worker imports its adjacent helper. An earlier URL-fragment approach failed under service-worker caching and was replaced by real versioned paths. The version also covers the distributed notice. The current output version is `v-caa95f3cac706ae8`.

The host passes validated runtime metadata into its short-lived renderer and displays download progress. Existing worker ownership, explicit termination, input limits, low-memory guard and output-PDF parsing remain in place. The conversion button continues using LibreOffice; the separately labelled basic text exporter remains opt-in. No processing backend, paid dependency or document-conversion API was added.

Vercel rewrites exclude engine, renderer and compiled asset paths; missing assets must return 404. MIME headers cover JSON metadata and compressed payloads. Existing COOP/COEP and nosniff headers remain. Dev/production-preview middleware follows the same engine MIME/404 contract. The service worker uses a fresh NetworkFirst manifest cache and versioned CacheFirst engine cache; it ignores the old v1 cache. Ordinary app/renderer assets are precached; the large engine is cached on demand after conversion is requested.

The existing control area now has separate, spaced rows, aligned checkbox text, a styled font selector and readable progress messages. No other page was redesigned.

## Measured sizes and hosting

| Asset | Original bytes | Delivery bytes |
| --- | ---: | ---: |
| `soffice.wasm` → `soffice.wasm.gzip` | 161,667,499 | 54,988,762 |
| `soffice.data` → `soffice.data.gzip` | 99,520,604 | 28,408,508 |
| `soffice.js` | 858,124 | 858,124 |
| `soffice.data.js.metadata` | 215,180 | 215,180 |
| `zetaHelper.js` | 9,207 | 9,207 |
| `zeta.js` | 42,946 | 42,946 |
| Application `office-thread.js` | 1,500 | 1,500 |
| `NOTICE.txt` | 3,316 | 3,316 |

The six upstream originals total **262,313,560 bytes (250.16 MiB)**. Delivery engine/worker files total **84,524,227 bytes (80.61 MiB)**, excluding manifest and notice. The entire verified `web/dist` is **89,704,552 bytes (89.70 MB / 85.55 MiB)**. Its largest file is 54.99 MB. No uncompressed WASM/data file is published.

[Vercel's current limits documentation](https://vercel.com/docs/limits#static-file-uploads) specifies a 100 MB Hobby CLI **source upload** limit, rather than documenting that figure as a universal per-output-file allowance. The build enforces a conservative 100,000,000-byte ceiling for each file **and the whole static output**. It fits both of those local checks; the raw WASM would not. Git deployments download the engine during build instead of committing or uploading it as source. Actual Vercel asset acceptance, response headers and end-to-end behavior still require an authorized preview deployment. No plan upgrade or external asset host has been introduced or shown necessary by these measurements. Browser cache storage and the account's bandwidth allowances still apply.

## Executed verification

Tests used the existing **authored synthetic** `complex-report.docx`, not the previously converted personal CV. It contains a logo, merged/bordered table, numbering, hyperlink, headers/footers and an explicit page break. Matching Arial/Calibri files were selected from the test machine; FileFlow does not distribute those fonts.

| Check | Result |
| --- | --- |
| Empty, isolated web-root installer | Downloaded all six public assets and verified every pinned original; no parent-directory dependencies |
| Same inputs packaged in isolated and regular roots | Same runtime version and manifest |
| Missing local engine source | Preparation exited 1; no silent successful build |
| Full mandatory production build | Passed; all packaged and decompressed hashes validated; 38 PWA precache entries |
| Actual built page: select DOCX → Convert → Save | Valid two-page PDF downloaded and independently opened |
| Required runtime requests | All seven engine/worker files plus manifest succeeded; no raw WASM/data request |
| Missing runtime/renderer URLs | Real HTTP 404, not SPA HTML |
| Cross-origin isolation | Enabled in actual browser |
| Old v1 HTML manifest cache | Deliberately seeded; ignored by the current converter |
| Networking disabled, page reloaded, DOCX selected again | Second real conversion and PDF download passed using the eight cached runtime entries |
| Request privacy | No external-origin HTTP requests or document POST requests during both successful conversions |
| Simulated HTTP-200 HTML instead of compressed engine | Meaningful download error; no Save button, no text fallback, renderer frame removed |
| Controls at 320 / 390 / 1920 px | Separate checkbox/font rows, no overlapping bounds, file-field or page overflow; screenshots saved |
| Unit tests | 39 passed |
| Desktop/mobile-emulation browser regressions | 42 passed; includes existing tools and failure paths |
| Other built PWA tools with networking disabled | PDF-to-Excel workbook and editable PDF-to-DOCX reopened successfully; no API requests |

The final production button run took about **10.9 seconds online and 9.8 seconds offline**, including Save and result checks on this machine. Earlier runs during concurrent testing were slower. These are local loopback observations, not WAN download speeds or Android timings. The engine's existing 1 GiB reserved WASM memory requirement remains; this repair does not establish a new peak-memory measurement.

Saved PDFs were independently parsed and rasterized at 90 DPI without resizing. Both packaged and offline outputs are **pixel-identical to the previous high-fidelity WASM output** for this fixture. They preserve its normalized text, dimensions, painted logo, hyperlink and used fonts. Two pages of 612 × 792 points, one painted image, one hyperlink, ArialMT/Calibri/Calibri-Bold. Pixel identity also covers the previous output's table and pagination. Against native Microsoft Word's existing reference, RGB mean absolute errors are **2.620832** and **0.194988** for pages 1 and 2. They are close, not exact Word output; font substitution and LibreOffice/Word layout differences remain relevant.

Evidence, intentionally ignored by Git:

- `.tools/production-packaging/comparison.html` — live before / corrected controls / PDF comparison links
- `.tools/production-packaging/live-browser-before.json`, `live-before-desktop.png`, `live-before-mobile.png` — actual deployed failure and controls
- `.tools/production-packaging/browser/warm.pdf` and `offline.pdf`
- `.tools/production-packaging/browser/results.json` — runtime responses, cache keys, UI bounds, privacy and failure checks
- `.tools/production-packaging/browser/comparison.json` and `comparison.html`
- `.tools/production-packaging/browser/comparison-page-1.png` and `comparison-page-2.png` — Word reference / packaged / offline
- `.tools/production-packaging/browser/controls-320.png`, `controls-390.png`, `controls-1920.png`
- `.tools/production-packaging/live-before.jsonl` — live-site missing-engine observations
- `.tools/production-packaging/clean-web/` — isolated installer result
- `.tools/backend-free-evidence/offline-web.json` — other offline converters

## Files changed

| Files | Purpose |
| --- | --- |
| `web/scripts/prepare-office-runtime.mjs` | Mandatory, self-contained pinned preparation and compression |
| `web/scripts/verify-office-output.mjs` | Final asset/hash/size verification |
| `web/scripts/office-runtime-lock.json` | Moved from root scripts; records embedded engine build ID |
| `web/licenses/office-runtime-notices.md` | Moved from docs; corrected Qt license disclosure |
| `scripts/setup-office-runtime.mjs` | Compatibility entry point |
| `web/package.json` | Automatic preparation and validation around the production build |
| `web/public/office-renderer/main.js` | Download, verify and decode engine; progress; versioned worker |
| `web/public/office-renderer/office-thread.js` | Adjacent versioned helper import; unchanged UNO document rendering |
| `web/src/lib/officeWasm.ts` | Current manifest validation, progress forwarding and meaningful errors |
| `web/vercel.json` | Asset routing, MIME and immutable compressed-file caching |
| `.vercelignore` | CLI source upload restricted to web source; excludes fixtures, generated engine, caches and environment files |
| `web/vite.config.ts` | Preview MIME/404 behavior and versioned PWA caching |
| `web/src/screens/tools/DocumentConverter.tsx`, `web/src/styles.css` | Existing controls' spacing, alignment and styling |
| `web/src/lib/officeWasm.test.ts`, `web/e2e/word-pdf-renderer.spec.ts` | Missing/stale-runtime safeguards |
| `scripts/word-pdf-offline-check.mjs` | Actual production button, offline, host/asset/UI/failure verification; optional deployed URL |
| `scripts/compare-office-packaging.py` | Independently open/compare PDFs and generate visual evidence |
| `README.md`, `docs/word-to-pdf-rendering.md`, this report | Accurate setup, current paths, results and release instructions |

## Remaining release work and deployment steps

**The live site has not been repaired until an authorized deployment passes the same browser test.** Vercel login and read-only project inspection are available, but no preview was created because the user prohibited deployment without approval. No push was made that could trigger an automatic deployment.

There is also a pre-existing distribution issue: the reviewed binary's complete corresponding source, build instructions and component license materials have not been assembled. The binary embeds build ID `efaf0670b4d055f838a2849becb10f08aa06a257`; official source pointers alone have not been verified as a complete match. [Qt's own 5.15 WASM documentation](https://doc.qt.io/archives/qt-5.15/wasm.html#licenses) lists GPLv3 or commercial terms, and the [referenced allotropia Qt fork's plugin source](https://github.com/allotropia/qtbase/blob/5.15.2%2Bwasm/src/plugins/platforms/wasm/qwasmwindow.cpp) confirms them. Do not treat the whole engine as MIT/LGPL-only. A free open-source distribution is a possible route, but the corresponding-source and applicable notice obligations must be completed before public release. No commercial license was added, purchased or assumed.

After that release work and deployment approval:

1. Commit the source changes, including `web/scripts/` and `web/licenses/`. Keep fixtures, local fonts, runtime binaries and `.tools/` out of Git. Review automatic production deployment settings before pushing.
2. Keep Vercel Root Directory **`web`**, Node **24.x**, Vite, Build Command **`npm run build`**, Output Directory **`dist`**. No conversion API environment variables, credentials or paid hosting are required by this path. Build networking must reach the pinned engine download URLs. Failures must fail deployment.
3. Link the repository root to the existing project with `vercel.cmd link --project qeilvra-converter-app` if necessary. Create a **preview** from that linked repository root with `vercel.cmd deploy` (omit `--prod`), or use an approved non-production Git branch preview. The root `.vercelignore` restricts CLI source uploads to web source and excludes its generated engine, node_modules, test output and environment files. No local test documents should be uploaded.
4. Review build logs for all original digest checks and final `verify-office-output` PASS. Check `/office-runtime/manifest.json` returns JSON, versioned `.gzip` responses return gzip payloads, JS returns JavaScript, missing asset paths return 404 and isolation headers are present.
5. Run the same actual browser-button test against the preview URL:

   ```powershell
   node scripts/word-pdf-offline-check.mjs --base-url=https://APPROVED-PREVIEW.vercel.app
   ```

   Preview protection must permit that browser session. It uses the synthetic local fixture and local font files, performs conversion in-browser, checks every runtime request and then reloads/converts with networking disabled. It never deploys or POSTs the DOCX. Evidence goes to `.tools/production-packaging/deployed-browser/`; compare those PDFs as well before claiming live success.

6. Only after preview verification and production approval, promote the verified deployment in Vercel or run `vercel.cmd promote APPROVED_PREVIEW_URL`. Reload existing app tabs so they use the new service worker. Repeat the deployed browser check against `https://qeilvra-converter-app.vercel.app`.

Local reproduction:

```powershell
npm.cmd --prefix web ci
npm.cmd --prefix web run build
npm.cmd --prefix web test
npm.cmd --prefix web run test:e2e
node scripts/word-pdf-offline-check.mjs
.tools/conversion-venv/Scripts/python.exe scripts/compare-office-packaging.py
node scripts/verify-offline-web.mjs
```

The browser scripts need Playwright Chromium and the existing synthetic fixtures; the comparison script needs PyMuPDF and NumPy. They are local verification prerequisites, not production dependencies. On this workspace, set TEMP/TMP to `.tools/runtime-tmp` if the system drive has insufficient space. Android was not changed; browser emulation is not a claim of native Android LibreOffice compatibility.
