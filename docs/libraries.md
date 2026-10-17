# Processing library review

Versions below are the versions resolved by the successful production build on 2026-10-16. License values come from each installed package manifest.

| Package | Resolved | License | FileFlow use |
| --- | ---: | --- | --- |
| PDF.js (`pdfjs-dist`) | 6.4.299 | Apache-2.0 | Dynamically loaded PDF text extraction |
| `pdf-lib` | 1.17.1 | MIT | Merge, copy, reorder, delete and rotate PDF pages; structural save |
| jsPDF | 4.2.1 | MIT | Image-to-PDF and text-focused DOCX-to-PDF output |
| ExcelJS | 4.4.0 | MIT | Generate and independently reload valid XLSX workbooks |
| `docx` | 9.11.0 | MIT | Generate valid DOCX packages from selectable PDF text |
| JSZip | 3.10.2 | MIT or GPL-3.0-or-later | Used under its MIT option to read DOCX package XML |
| Lucide React | 0.545.0 | ISC | Consistent interface icons |

The project deliberately does not use iText or another AGPL/commercial PDF engine. Package installation was followed by `npm audit`; the final lockfile reported zero known vulnerabilities. Browser conversion quality remains bounded by the implemented workflows rather than inferred from library names.

The Android build targets API 36 because Google Play requires new apps and updates to target Android 16/API 36 from 31 August 2026. It uses AGP 9.4, whose official compatibility table specifies Gradle 9.6, Build Tools 36.0.0 and JDK 17.
