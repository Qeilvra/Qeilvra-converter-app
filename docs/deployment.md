# Deployment

## Processing service

The supported deployment is the root `docker-compose.yml`. It builds Java 21, LibreOffice Writer, qpdf, Python, `pdf2docx`, and Noto/Liberation fonts into an isolated worker image.

```bash
docker compose build processing
docker compose up -d processing
curl http://127.0.0.1:8080/api/v1/health/capabilities
```

Configuration:

| Variable | Default | Purpose |
| --- | --- | --- |
| `FILEFLOW_ALLOWED_ORIGINS` | `http://localhost:5173` | Exact comma-separated web origins |
| `FILEFLOW_WORKER_CONCURRENCY` | `2` | Maximum simultaneous external engine jobs |
| `FILEFLOW_REQUESTS_PER_MINUTE` | `30` | Per-client processing POST limit per server instance |
| `FILEFLOW_LIBREOFFICE_COMMAND` | `soffice` | LibreOffice executable |
| `FILEFLOW_QPDF_COMMAND` | `qpdf` | Optional structural optimizer |
| `FILEFLOW_PDF_TO_DOCX_COMMAND` | `python3 -m pdf2docx.main convert {input} {output}` | Replaceable PDF-to-DOCX adapter; keep both placeholders |
| `PORT` | `8080` | Internal HTTP port |

The root Compose file accepts `FILEFLOW_ALLOWED_ORIGINS` and `FILEFLOW_WORKER_CONCURRENCY` from the launching environment. Keep the default loopback-only port binding when a local reverse proxy terminates HTTPS.

Terminate TLS at a reverse proxy and expose only `/api/v1`. Add IP/user rate limiting and request-body limits at that proxy. Keep the container private, do not mount persistent document storage, do not log multipart bodies, and alert only on operation, status, duration, and aggregate sizes. The health endpoint contains no document data.

The included compose limits the service to 2 CPUs, 1.5 GB RAM, 256 PIDs, a 768 MB temporary filesystem, no Linux capabilities, and a read-only root filesystem. Tune concurrency only after load tests; LibreOffice conversions are memory-heavy.

## Web

```bash
cd web
npm ci
$env:VITE_PROCESSING_API_URL='https://files.example.com/api/v1'
npm run build
```

Deploy `web/dist` behind HTTPS with SPA fallback. If the API shares the same origin, reverse-proxy `/api/v1` and omit the variable. Add a restrictive CSP while allowing the PDF worker and configured API origin. The PWA caches application assets, not documents.

### Vercel repair requirements

Choose **Root Directory `web`**, framework Vite, install `npm ci`, build `npm run build`, output `dist`. The checked-in `web/vercel.json` supplies the SPA fallback for direct links such as `/tools/pdf-to-word`. It deliberately does not turn `/api/*` requests into HTML. No Java worker is deployed by this frontend build.

Set `VITE_PROCESSING_API_URL=https://YOUR-ACTUAL-BACKEND/api/v1` in the relevant Vercel Production/Preview environment **before building**, then redeploy with approval. An environment change cannot change an already built JS bundle. Do not put a server secret, service-role key, or private API credential in a `VITE_*` variable. Set backend `FILEFLOW_ALLOWED_ORIGINS=https://qeilvra-converter-app.vercel.app` (plus exact preview origins if needed). A bare origin, `/api`, duplicated `/api/v1`, stale URL, or frontend domain without a worker route is not a valid configured backend base.

The owner confirmed no processing backend has been deployed. See [hosting options and live 404 evidence](hosting-options.md). The live website is unchanged by local repairs. Vercel supports external reverse-proxy rewrites, but such a rewrite still needs a real HTTPS worker and must allow multipart uploads and conversion durations. Direct browser-to-worker requests avoid an unnecessary function proxy.

Verify after any approved deployment:

```powershell
curl.exe https://YOUR-ACTUAL-BACKEND/api/v1/health/capabilities
node scripts/verify-processing.mjs https://YOUR-ACTUAL-BACKEND/api/v1 --require-engines
```

Run the script from the repository root after `npm --prefix web ci`; it sends only generated synthetic files. Inspect each operation's availability rather than treating HTTP 200 on the capabilities endpoint as proof that Office engines work. An overall `degraded` result can still provide working table extraction and PDF compression.

`--require-engines` makes the command fail when an Office engine is unavailable; omit it only for an explicitly partial local-service check. The report labels unavailable conversions `UNVERIFIED`, never passed conversions.

References: [Vite on Vercel](https://vercel.com/docs/frameworks/frontend/vite), [external rewrites](https://vercel.com/docs/routing/rewrites), [Vite environment replacement](https://vite.dev/guide/env-and-mode).

## Android

Set one of:

```properties
# User-level gradle.properties; do not commit secrets
fileflowProcessingApiUrl=https://files.example.com/api/v1
```

or `FILEFLOW_PROCESSING_API_URL`. Production builds reject cleartext service URLs; `http://10.0.2.2` is accepted only for emulator development. Build with SDK 36 and JDK 17+ using the checked-in wrapper. Release signing values remain external environment variables.

## Engine go-live gates

1. Install the exact production font set, including fonts used by the English/Urdu test corpus. Font substitutions change pagination.
2. Run the quality matrix in `quality-benchmark.md` against LibreOffice, Microsoft Word/Adobe reference output, and the proposed PDF-to-DOCX engine.
3. Decide whether the existing PDF-to-DOCX baseline meets the measured quality and licensing requirements. A paid engine requires explicit approval; none is added by this audit. Never deploy trial-watermarked output. Review transitive PyMuPDF AGPL/commercial terms as well as the top-level package license.
4. Run malware scanning in front of the worker if required by the threat model, without retaining or sending documents to an unapproved third party.
5. Verify deletion, timeout, concurrency, proxy rate limit, backup exclusion, and log redaction in the production environment.

Docker image execution was not completed on the development machine because the Docker Linux daemon was not running. Unit and parser-quality tests do not require Docker; actual LibreOffice/pdf2docx integration is therefore a release blocker until run in the target container.
