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

Web dependencies retain their permissive choices: pdf-lib (MIT), PDF.js (Apache-2.0), jsPDF (MIT), ExcelJS (MIT), JSZip under MIT, React (MIT), and Lucide (ISC). The backend-free implementation promotes `docx` and ExcelJS to lazy runtime dependencies and adds PDF.js with a same-origin worker. See [current decisions](backend-free.md) for quality limits and the optional bundled native ML Kit Latin OCR build. The default Android build excludes the OCR dependency/model; ML Kit uses Google SDK/model terms rather than an open-source license.

Licenses must be rechecked from the resolved release during every dependency upgrade. Commercial SDKs must not be added in trial mode or committed with license keys.

## Audit correction — transitive engine licensing

`pdf2docx` depends on PyMuPDF/MuPDF. The upstream PyMuPDF documentation specifies AGPL or commercial licensing; a top-level MIT entry does not establish that the complete conversion stack is permissively licensed. Public hosting/distribution needs a documented compliance decision for the exact resolved versions. No commercial license or paid engine was added during the audit. [PyMuPDF licensing](https://pymupdf.readthedocs.io/en/latest/about.html#license-and-copyright).

The installed baseline's published limitations include left-to-right text, missing semantic header/footer and list reconstruction, and no integrated OCR. These limitations are relevant to Urdu/RTL and must be reflected in quality claims. [Version 0.5.8 features and limitations](https://pypi.org/project/pdf2docx/0.5.8/).

Dependency audit scope: `npm audit --json` returned zero advisories for the resolved web tree on October 22, 2026. This is not a Maven, Android, container, or Python security certification. PDFBox's official advisories for CVE-2026-23907 and CVE-2026-33929 concern the examples module's embedded-file extraction; FileFlow does not call that example. Continue release scanning rather than presenting unrelated example code as a demonstrated application exploit. [PDFBox security](https://pdfbox.apache.org/security.html).

Primary references:

- LibreOffice command-line PDF export and filters: <https://help.libreoffice.org/latest/en-US/text/shared/guide/pdf_params.html>, <https://help.libreoffice.org/latest/ast/text/shared/guide/convertfilters.html>
- qpdf source/license: <https://github.com/qpdf/qpdf>
- `pdf2docx` maintenance notice: <https://github.com/ArtifexSoftware/pdf2docx/blob/master/README.md>
- Ghostscript optimization/licensing: <https://www.ghostscript.com/blog/optimizing-pdfs.html>, <https://ghostscript.com/releases/gsdnld.html>
- Adobe PDF Services: <https://developer.adobe.com/document-services/docs/overview/pdf-services-api/>
- Aspose.PDF Java PDF-to-Word: <https://products.aspose.com/pdf/java/conversion/pdf-to-word/>
