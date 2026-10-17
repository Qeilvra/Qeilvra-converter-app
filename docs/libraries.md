# Processing engine and license review

| Component | License/status | Use and decision |
| --- | --- | --- |
| LibreOffice Writer | MPL-2.0 / LGPLv3+ | Selected for real DOCX rendering. Install matching fonts and benchmark Word-specific features. |
| Apache PDFBox 2.0.35 | Apache-2.0 | PDF validation, embedded-image recompression, and output validation. |
| qpdf | Apache-2.0 | Selected optional object-stream/stream optimization after image recompression. |
| Tabula 1.0.5 | MIT | Selected for ruled and stream table detection on text PDFs. No OCR. |
| Apache POI 5.4.1 | Apache-2.0 | DOCX validation, valid typed XLSX generation, and synthetic acceptance fixtures. |
| `pdf2docx` 0.5.8 | MIT; upstream says no longer actively maintained | Bundled only as the legal open-source baseline. Not sufficient by itself for the final professional-fidelity claim. |
| Ghostscript | AGPL or commercial license | Evaluated but not bundled. Its presets can strongly alter images and its licensing needs a product decision. PDFBox + qpdf avoids this dependency. |
| Aspose.PDF for Java | Commercial | Candidate on-premises replacement for PDF-to-DOCX through the command adapter or a licensed Java integration. License purchase and benchmark required. |
| Adobe PDF Services | Commercial cloud service | Candidate PDF export/OCR service with Java SDK. Requires credentials, regional/data-processing approval, network egress, and usage cost. |
| OpenCV | Apache-2.0 | Evaluated for scanning. Not added because the implemented edge/homography pipeline avoids NDK/WASM size; adopt if the scanner corpus fails. |

Web dependencies retain their previous permissive choices: pdf-lib (MIT), PDF.js (Apache-2.0), jsPDF (MIT), ExcelJS (MIT), JSZip under MIT, React (MIT), and Lucide (ISC). `docx` and ExcelJS remain development/E2E fixture dependencies even though high-fidelity document conversion no longer uses browser text extraction.

Licenses must be rechecked from the resolved release during every dependency upgrade. Commercial SDKs must not be added in trial mode or committed with license keys.

Primary references:

- LibreOffice command-line PDF export and filters: <https://help.libreoffice.org/latest/en-US/text/shared/guide/pdf_params.html>, <https://help.libreoffice.org/latest/ast/text/shared/guide/convertfilters.html>
- qpdf source/license: <https://github.com/qpdf/qpdf>
- `pdf2docx` maintenance notice: <https://github.com/ArtifexSoftware/pdf2docx/blob/master/README.md>
- Ghostscript optimization/licensing: <https://www.ghostscript.com/blog/optimizing-pdfs.html>, <https://ghostscript.com/releases/gsdnld.html>
- Adobe PDF Services: <https://developer.adobe.com/document-services/docs/overview/pdf-services-api/>
- Aspose.PDF Java PDF-to-Word: <https://products.aspose.com/pdf/java/conversion/pdf-to-word/>
