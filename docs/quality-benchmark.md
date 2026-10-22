# Conversion quality benchmark

## Audit measurements — October 22, 2026

`scripts/benchmark-pdf2docx.py` executed the real `pdf2docx` 0.5.8 converter on three generated PDFs and reopened every DOCX with python-docx. Runs with PyMuPDF 2.0 and 1.26.7 produced the same checked structure. The existing adapter is now pinned to tested PyMuPDF 1.26.7 and python-docx 1.2.0 for reproducibility and to avoid unnecessary PyMuPDF 2 layout/ONNX dependencies. This is not a full dependency security certification; Linux container resolution still needs verification.

| Synthetic fixture | Checked result with PyMuPDF 1.26.7 |
| --- | --- |
| Styled two-page document | 7/7 expected text fragments; two sections; three bold runs and one italic run |
| Ruled table and embedded raster | 7/7 text fragments; one editable table; one inline image; leading-zero and long identifiers retained |
| Two text columns | 11/11 expected fragments; three sections; visual column positioning/reading order not certified |

The footer string survived, but there were **zero semantic Word footers**. Header/footer reconstruction is therefore a confirmed limitation. Source paragraphs, tables and pictures were inspected structurally; no Word/LibreOffice renderer was available for page-image comparison. These 25 fragments do not establish a general text-completeness percentage, pixel-perfect layout, merged-cell reconstruction, complex Unicode fidelity, or real-world quality across the ten-document release corpus.

Observed conversion-plus-package-inspection times with 1.26.7 were 515/1155/599 ms. The 2.0 run measured 357/554/361 ms under a different concurrent workload. These are descriptive local measurements, not evidence that the pin speeds conversion. Windows PyMuPDF wheel downloads were 18.4 MB for 1.26.7 versus 63.0 MB for 2.0; no claim is made about Android size or Linux image size.

Live HTTP checks in `scripts/verify-processing.mjs` additionally exercised real PDFBox/Tabula/POI extraction and export, numeric/date opt-in, unsafe numeric rejection, CORS, malformed inputs, a supported 500-blank-page fixture, 501-page rejection and scan rejection. Office availability is explicitly labeled UNVERIFIED when an engine is missing; `--require-engines` makes that a failed release gate. See the main audit for final runtime results. Browser remote-tool tests use explicit fixtures and do not count as conversion-engine evidence.

OCR, LibreOffice rendering, licensed competing engines, Microsoft Word/Adobe visual comparisons, RTL shaping and a complex 30–50 MB corpus remain unverified. No paid engine or OCR provider was added.

The audit also reproduced a genuine command configuration failure: `python -m pdf2docx` exits because the installed package has no `__main__`. The default is repaired to `python3 -m pdf2docx.main convert {input} {output}`. An independent `--cli` benchmark then reopened all three generated DOCX files and recovered the same25/25 fragments, table, image and tested styles. These CLI runs measured1543/1668/1754ms including subprocess startup; no universal performance claim follows.

## Historical automated results (2026-10-17)

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
