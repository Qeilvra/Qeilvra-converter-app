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
| `FILEFLOW_LIBREOFFICE_COMMAND` | `soffice` | LibreOffice executable |
| `FILEFLOW_QPDF_COMMAND` | `qpdf` | Optional structural optimizer |
| `FILEFLOW_PDF_TO_DOCX_COMMAND` | `python3 -m pdf2docx convert {input} {output}` | Replaceable PDF-to-DOCX adapter; keep both placeholders |
| `PORT` | `8080` | Internal HTTP port |

Terminate TLS at a reverse proxy and expose only `/api/v1`. Add IP/user rate limiting and request-body limits at that proxy. Keep the container private, do not mount persistent document storage, do not log multipart bodies, and alert only on operation, status, duration, and aggregate sizes. The health endpoint contains no document data.

The included compose limits the service to 2 CPUs, 1.5 GB RAM, 256 PIDs, a 768 MB temporary filesystem, no Linux capabilities, and a read-only root filesystem. Tune concurrency only after load tests; LibreOffice conversions are memory-heavy.

## Web

```bash
cd web
npm ci
set VITE_PROCESSING_API_URL=https://files.example.com/api/v1
npm run build
```

Deploy `web/dist` behind HTTPS with SPA fallback. If the API shares the same origin, reverse-proxy `/api/v1` and omit the variable. Add a restrictive CSP while allowing the PDF worker and configured API origin. The PWA caches application assets, not documents.

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
3. Purchase and configure the selected commercial PDF-to-DOCX license if its results beat the open-source baseline. Never deploy trial-watermarked output.
4. Run malware scanning in front of the worker if required by the threat model, without retaining or sending documents to an unapproved third party.
5. Verify deletion, timeout, concurrency, proxy rate limit, backup exclusion, and log redaction in the production environment.

Docker image execution was not completed on the development machine because the Docker Linux daemon was not running. Unit and parser-quality tests do not require Docker; actual LibreOffice/pdf2docx integration is therefore a release blocker until run in the target container.
