# Document quality suite

The automated suite generates synthetic, non-sensitive fixtures in code so opaque user documents are not committed. `server/src/test/java/com/fileflow/server/service/ProcessingQualityTest.java` creates:

- an image-heavy PDF with selectable text;
- a ruled financial-style table PDF with text and numeric cells;
- a multipage English/Urdu DOCX with a formatted title, colors and sizes, merged table, two images, header, footer, and page-number field.

The browser E2E suite generates PDFs/DOCX/images and validates downloaded PDF, XLSX, ZIP, JPEG, scanner, page editing, and responsive flows.

Before release, add licensed/internal representative samples to an external encrypted test-data location using these IDs (do not commit customer files):

1. `pdf-simple-text`
2. `pdf-complex-multipage`
3. `pdf-images`
4. `pdf-tables`
5. `pdf-multicolumn`
6. `pdf-headers-footers`
7. `pdf-scanned`
8. `docx-complex-formatting`
9. `pdf-image-heavy`
10. `multilingual-en-ur`

Record source checksum, expected pages/tables/images, license/provenance, and benchmark result. Never fabricate expected data for a scan that requires OCR.
