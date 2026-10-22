# Architecture

## Processing boundary

| Operation | Execution | Engine |
| --- | --- | --- |
| Image conversion/compression, Prepare for Upload | Device | Canvas on web; Android Bitmap APIs |
| Scanner capture, edge detection, four-corner correction, filters, multipage PDF | Device | TypeScript/Canvas projective transform; native Java/Android Matrix transform |
| PDF merge, split, rotate, reorder | Device | pdf-lib; pdfbox-android |
| PDF to DOCX | Isolated service | Configurable command adapter; container baseline is `pdf2docx` |
| DOCX to PDF | Isolated service | LibreOffice Writer headless, isolated per-job profile |
| PDF compression | Isolated service | PDFBox image downsampling/recompression, then optional qpdf object streams |
| PDF table recognition/XLSX | Isolated service | Tabula lattice/stream detection and Apache POI |

The clients never describe a service-backed operation as browser-only or device-only. The web client can use Supabase Auth for email/password login and a persisted browser session. Supabase is not used for document storage: there is no permanent document database or public object-storage URL.

## Request lifecycle

```text
client validation -> HTTPS request -> bounded admission/rate limit -> parser validation
                  -> unique temporary workspace -> engine -> parser validation
                  -> private response -> recursive workspace deletion
```

The service limits files to 50 MB, PDFs to 500 pages, external engine execution to 120 seconds, and default processing concurrency to two jobs. Reviewed-table JSON is limited to 8 MiB. In-JVM table/PDF work has cooperative deadlines rather than a hard parser sandbox. External processes are invoked with argument arrays rather than a shell. LibreOffice receives a unique user profile. The configured container is non-root, read-only, without Linux capabilities, with a bounded tmpfs, memory, CPU, and PID limit; its runtime remains unverified here because the Docker daemon is unavailable.

## Fidelity decisions

DOCX-to-PDF uses a real document renderer, so tables, images, headers, footers, lists, margins, page breaks, and supported fields are rendered instead of reconstructed from text. Results still depend on installed fonts and LibreOffice compatibility with Word-only features.

PDF-to-DOCX is intrinsically harder because PDF stores positioned drawing instructions rather than Word document semantics. The server adapter permits replacement of the baseline without changing clients. The bundled `pdf2docx` baseline is editable and layout-aware, but is no longer actively maintained; it is not the final engine for a commercial fidelity claim. A licensed on-premises Java engine such as Aspose.PDF, or an approved cloud service such as Adobe PDF Services, must win the representative benchmark before production launch.

PDF compression never rasterizes whole pages. It only replaces an embedded opaque image when the replacement stream is smaller, keeps text/content streams intact, validates the result, and returns the original bytes when no safe size reduction is achieved.

Table recognition uses ruled-table detection first and stream detection second. It preserves rows, columns, values, multiple tables, and worksheet separation. It flags structurally inconsistent stream cells. It does not invent OCR data or merged cells that are not recoverable from PDF geometry.

## Scanner

The web and Android scanners perform automatic edge estimation, expose four manual corners, apply a projective transformation, then apply color, grayscale, or black-and-white enhancement. Pages can be rotated, reordered, removed, previewed, and exported. The web uses camera capture where supported and an import fallback. This implementation avoids OpenCV/NDK/WASM size and update overhead; OpenCV remains the upgrade path if the benchmark shows the lightweight detector is insufficient for shadows, folded pages, or complex backgrounds.

## State

Source bytes live in component/view-model memory and job workspaces. Web Recent stores metadata by default; users can explicitly enable local IndexedDB output retention, bounded to recent records. Android recent entries contain metadata and app-cache output references, not a cloud library. Cache eviction can make an output unavailable. Object URLs are revoked. Android uses the Storage Access Framework and does not request broad storage access. Temporary workspace deletion is attempted on normal request completion; process crashes, filesystem errors and host retention require operational verification.
