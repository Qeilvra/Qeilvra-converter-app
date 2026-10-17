# Conversion quality benchmark

## Automated results (2026-10-17)

| Check | Result |
| --- | --- |
| PDF signature plus real parser validation | Pass |
| Image-heavy PDF high compression | Pass: output smaller, one page valid, selectable text retained, embedded image optimized |
| Ruled PDF table extraction | Pass: rows/columns recovered and numeric score exported as a numeric XLSX cell |
| DOCX acceptance fixture structure | Pass: English/Urdu, formatting, merged table, two images, header/footer, page field, page break |
| Backend result parser validation | Implemented; direct engine-path execution awaits the container run. The fixture DOCX and compression PDF reopen successfully in tests. |
| Web unit tests | 13/13 pass |
| Web desktop/mobile E2E | Pass: 10/10 scenarios across desktop and mobile projects |
| Android JVM unit build | Pass |
| LibreOffice/pdf2docx container conversion | Blocked locally: Docker Linux daemon not running |
| Microsoft Word/Adobe/PDFgear reference comparison | Not executed; applications/corpus not available in this environment |

## Release benchmark protocol

For each of the ten fixture IDs in `test-fixtures/README.md`, run every applicable engine and record output validity, page count, file size, elapsed time, text completeness/reading order, image preservation, table cell accuracy, merged cells, fonts/styles/colors, margins, headers/footers, lists, columns, pagination, editability, and rendered page difference at the same DPI.

Use Microsoft Word's PDF export as the DOCX rendering reference when available. For PDF-to-DOCX, compare the bundled baseline with a licensed Aspose.PDF evaluation in a controlled environment (never ship watermarked output) and Adobe Export PDF if cloud processing is approved. Adobe/Acrobat/PDFgear outputs are benchmarks, not dependencies unless separately licensed.

No universal pixel-perfect and fully editable PDF-to-DOCX threshold is possible. Define per-corpus gates before launch; a suggested minimum is 99% text completeness, 95% table-cell accuracy, zero missing images, valid output in Word/LibreOffice, and human approval of pagination on all critical templates.
