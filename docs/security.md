# Security and privacy

## Truthful data flow

Local operations: image tools, scanner, PDF page editing/merge/split/rotate, metadata history. Their bytes do not leave the device.

Service operations: PDF-to-DOCX, DOCX-to-PDF, PDF compression, and PDF table recognition. These transfer a copy to the configured FileFlow service over HTTPS. Job cleanup is attempted on normal completion and failure; failed deletion and abrupt host/process termination require operational checks. This is not a guarantee of immediate erasure from memory, host snapshots, proxies, or storage hardware. The web processing client sends no account cookies or tokens.

## Implemented controls

- Magic-byte plus real PDF parser validation; DOCX ZIP structure, entry-count, and 200 MB expanded-size validation.
- 50 MB upload and 500-page limits, 120-second engine timeout, fair concurrency semaphore, and bounded table export.
- Unpredictable temporary workspaces for external engines; cleanup runs from `AutoCloseable` on success and failure. Cleanup failures are logged without document names or content.
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

The web client supports Supabase email/password authentication when `VITE_SUPABASE_URL` and `VITE_SUPABASE_PUBLISHABLE_KEY` are configured. Only the browser-safe publishable key belongs in the web build; a service-role key must never be exposed there. Logout uses local scope so it ends the current browser session without unexpectedly signing the user out on every device.

The document-processing service does not validate Supabase access tokens. Web login must therefore not be treated as protection for a publicly reachable processing API. A deployment that restricts processing to signed-in users must validate the Supabase JWT at a trusted API gateway or in the service. Guest processing instead relies on the existing request, resource, and abuse controls and does not provide account-specific authorization.

## Audit additions — October 22, 2026

Basic tools intentionally remain available without accounts. The worker currently implements **anonymous, bounded processing**, not account-protected processing. If a deployment requires user-specific authorization, quotas, or billing, it must add JWT validation at a trusted gateway or service before claiming those guarantees. Never embed a shared privileged secret into a browser build or Android APK.

New application admission controls cover every processing POST before document parsing: configured job concurrency, a bounded per-client-IP rate map (30 requests/minute by default), busy responses with `Retry-After`, and an 8 MB reviewed-table JSON limit. Rejected origins are handled before work begins. The application deliberately does not trust arbitrary `X-Forwarded-For`; a reverse proxy must establish a trusted client-IP policy, otherwise all users behind it share a rate bucket. These in-process controls reset on restart and are not a distributed abuse-prevention system.

Engine subprocesses use bounded time and process-tree termination. PDF compression checks image pixel counts before decoding. DOCX validation now parses the package and refuses external linked relationships rather than allowing the renderer to retrieve arbitrary remote resources. Table export validates structure, cell lengths, column types, and Excel precision. Unhandled conversion failures return a generic message rather than exposing stack traces.

Java PDF/table parsing remains in the service JVM. Cooperative deadlines between pages/resources cannot forcibly interrupt a parser stuck inside a single library call. Keep the container memory/CPU/PID limits and restart policy; a process-per-job parser sandbox is a future hardening option before hostile public workloads. Real malware corpus testing and full dependency/image scans remain release gates.

Web output retention is optional, local, and off by default. It uses IndexedDB; Clear removes retained copies as well as metadata. Android generates app-cache outputs and records references for Save/Share; clearing Recent is not an operating-system secure wipe. Both storage models must be disclosed accurately.

The native app already includes Google Mobile Ads/UMP with test identifiers. Document content is not intentionally submitted to Ads, but SDK network activity and device/advertising identifiers require a separate consent, privacy-policy, and Google Play Data safety review. Do not describe the entire app as network-free merely because a selected tool processes documents locally.

The audit found no tracked keystore/private-key files or matches for the checked private-key/AWS-access-key/Supabase-secret-key signatures in application sources and docs. This limited scan does not certify all git history or arbitrary secret formats. Resolved web npm audit results and remaining dependency-scan gaps are recorded in `libraries.md`.
