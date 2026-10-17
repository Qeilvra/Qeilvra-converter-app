# Security and privacy

## Current data flow

All web file operations execute locally. There is no processing API, account system, document database, analytics SDK or public file URL. The service worker caches application assets, not user files.

## Controls

- File contents are checked using magic bytes before processing.
- Web inputs default to a 50 MB ceiling; image tools use a stricter 30 MB ceiling.
- Empty files, malformed PDFs, unsupported formats and encrypted PDFs return user-facing failures.
- Object URLs are revoked after replacement or screen disposal.
- Output success follows generation and measured size, never a filename change.
- Recent activity contains only filenames, operation, time and measured output size.

## Known risks and follow-up

Browser parsers process hostile inputs inside the browser origin. Dependencies must be kept patched and document parsers should remain isolated from application secrets (none are currently present). Large-file memory pressure varies by device. A future server conversion path requires content validation, sandboxed workers, CPU/memory/time quotas, rate limiting, non-public storage and automatic deletion before launch.
