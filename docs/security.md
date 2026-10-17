# Security and privacy

## Truthful data flow

Local operations: image tools, scanner, PDF page editing/merge/split/rotate, metadata history. Their bytes do not leave the device.

Service operations: PDF-to-DOCX, DOCX-to-PDF, PDF compression, and PDF table recognition. Their screens state that the file is transferred to the configured FileFlow service over HTTPS and deleted after the request. The clients send no cookies or credentials with web processing requests.

## Implemented controls

- Magic-byte plus real PDF parser validation; DOCX ZIP structure, entry-count, and 200 MB expanded-size validation.
- 50 MB upload and 500-page limits, 120-second engine timeout, fair concurrency semaphore, and bounded table export.
- One unpredictable temporary directory per request; cleanup runs from `AutoCloseable` on success and failure.
- No document-content logging, database, analytics payload, public storage URL, or permanent server path.
- External engines execute without a shell; client filenames do not become command arguments.
- DOCX and PDF engine results are reopened with Apache POI/PDFBox before success is returned.
- `Cache-Control: no-store`, no-referrer, no-sniff, frame denial, exact-origin CORS, no credentialed CORS.
- Container is non-root/read-only, drops all capabilities, forbids privilege escalation, and has CPU/RAM/PID/tmpfs limits.
- Compression returns original bytes when its candidate is not smaller and never reports that case as a reduction.

## Deployment controls still required

- TLS and HSTS at the reverse proxy.
- Proxy-level rate limiting, connection limits, and request-size enforcement.
- Network policy preventing worker access to unrelated internal systems. Adobe or another cloud engine requires an explicit data-processing approval before egress is enabled.
- Patched base images/dependencies, image and dependency scanning, operational monitoring without filenames or content.
- Periodic test proving `/tmp/fileflow-job-*` contains no completed jobs.

The service currently has no authentication because FileFlow has no account system. A public deployment must use abuse controls at the edge; if private documents require tenant authorization, add short-lived authenticated requests without introducing public document URLs.
