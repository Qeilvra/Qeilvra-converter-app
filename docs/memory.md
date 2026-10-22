# Project memory

- FileFlow uses the supplied blue-and-white glassmorphism reference and keeps mobile controls compact.
- Never fake conversion/compression success, silently upload a document, or describe a remote operation as local.
- Local: image tools, scanner, PDF page operations, and recent metadata. Service: PDF/DOCX conversion, PDF compression, and table recognition.
- Android remains native Java/XML.
- Supabase is used only for optional web authentication; no permanent document database, public document URL, or publication without owner authorization.
- PDF-to-DOCX baseline is configurable and editable/layout-aware, but `pdf2docx` is unmaintained; commercial engine selection is a release gate, not a hidden limitation.
- Scanned PDF table extraction requires a separately approved OCR engine and must never invent cells.
- Development machine: Node 24, JDK 21, Android SDK 36. Android tests compile when allowed to read the installed SDK. Docker CLI exists, but its Linux daemon was not running during verification.
