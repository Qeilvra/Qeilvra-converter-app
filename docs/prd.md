# FileFlow product requirements

## Product promise

FileFlow provides real file transformations without accounts, watermarks, fake success, or silent uploads. Formatting-sensitive operations use a disclosed secure processing service when browser/device engines cannot meet the quality bar.

## Acceptance baseline

- Output success requires a non-empty file reopened by an independent parser where practical.
- PDF-to-DOCX must reconstruct editable text/layout; it must not insert page screenshots as the document body.
- DOCX-to-PDF must use a document renderer, not extracted paragraphs.
- Compression must recompress/downsample actual embedded images, retain selectable text, measure results, and keep the original when the candidate is larger.
- Scanner must capture/import, automatically detect, expose four manual corners, perspective-correct, enhance, manage multiple pages, and export a valid PDF.
- PDF-to-XLSX must recover tables, expose uncertain cells for editing, and never fabricate OCR data.
- Each screen must state whether processing is local or service-backed.
- Controls remain keyboard/touch usable at a 320 px viewport.

## Release boundary

Implemented code covers the hybrid architecture and all listed workflows. Production release remains gated by actual container engine tests, the representative 10-document benchmark, physical Android camera/share tests, and selection/licensing of a PDF-to-DOCX engine if the open-source baseline misses the agreed threshold.
