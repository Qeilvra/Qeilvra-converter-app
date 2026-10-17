# FileFlow product requirements

## Product promise

FileFlow gives students, applicants and everyday office users a focused set of file tools without compulsory accounts, watermarks or silent uploads. The default path is local processing. Every success message must be backed by a valid generated file and measured output properties.

## Launch outcomes

1. A user can choose a tool without learning file-format terminology.
2. Local tools work after the PWA shell and required code chunks are cached.
3. Outputs can be downloaded or shared through the platform share sheet when supported.
4. Complex conversions state their limits before processing.
5. Recent activity stores metadata only and never claims the browser retained an output file.

## Acceptance baseline

- Reject empty, mislabeled and oversized inputs with a useful next action.
- Keep expensive work off critical navigation paths and lazy-load PDF processing.
- Do not report a compression target as met if the measured output exceeds it.
- Preserve source files; all outputs use new meaningful names.
- All controls are keyboard reachable and remain usable at a 320 px viewport.

## Current release scope

The web milestone implements image conversion/compression, image-to-PDF, PDF page editing/merge, structural PDF optimization, Prepare for Upload, PDF table extraction to editable XLSX, text-focused PDF-to-DOCX, text-focused DOCX-to-PDF, local history and settings.

OCR, automatic document-edge detection and high-fidelity Office layout rendering are not included in the local browser milestone and are disclosed in context.
