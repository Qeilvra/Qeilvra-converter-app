# Architecture

## Monorepo

- `web`: React + strict TypeScript + Vite. Tools call focused modules under `src/lib` and never make document-upload requests.
- `android`: native Java/XML shell and local processing modules. It deliberately avoids broad storage permission and uses the Storage Access Framework.
- `server`: absent. No current feature justifies uploading private documents to a server.

## Web boundaries

```text
screen -> signature/size validation -> local processing module -> measured result
       -> user review              -> download/share             -> metadata history
```

`lib/images.ts` owns canvas decoding, format conversion, image-to-PDF and iterative target-size search. `lib/pdf.ts` owns PDF parsing, table heuristics, Office export and page operations. `lib/files.ts` owns signature validation, downloads/shares, safe filenames and local metadata.

PDF.js is dynamically imported so the parsing engine and worker do not inflate the initial interaction path. Browser-native canvas handles images. No input bytes are placed in local storage.

## Conversion quality

PDF is a positioned page format, not a semantic table/document format. PDF-to-XLSX groups text by vertical position and exposes the result for correction. PDF-to-DOCX and DOCX-to-PDF intentionally describe their text-focused scope. A production high-fidelity Office service would require an isolated LibreOffice-based backend with informed consent, strict resource limits and immediate deletion; it is not silently emulated here.

## State and recovery

File bytes stay in component memory and object URLs are revoked when replaced. Navigation cancels ownership of those in-memory references. Metadata history is capped at 30 records. Source files remain untouched.
