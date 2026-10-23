FileFlow's office renderer uses an optional, locally hosted ZetaOffice / LibreOffice WebAssembly runtime. These generated files are excluded from Git. The application never loads a vendor CDN and never submits documents to one. Running the explicit setup script downloads public engine software only.

ZetaJS: Copyright (c) 2024 allotropia software GmbH and contributors. MIT license. Source and full license: https://github.com/allotropia/zetajs (the reviewed source revision is recorded in scripts/office-runtime-lock.json).

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

LibreOffice is available under MPL 2.0 and includes third-party works with other licenses. ZetaOffice's documented runtime source references are:

- https://git.libreoffice.org/core/+/refs/heads/distro/allotropia/zeta-24-2
- https://github.com/allotropia/emscripten/commits/fixed-3.1.65
- https://github.com/allotropia/qt5/tree/5.15.2%2Bwasm
- https://github.com/allotropia/qtbase/tree/5.15.2%2Bwasm
- https://www.libreoffice.org/licenses/

The integration code and local conversion experiment are verified. The CDN package's complete matching source archive, third-party notices and Qt LGPL relinking materials have not been assembled into a distributable FileFlow release. This setup is for local development/evaluation; it is not certification that copying the generated runtime into a published app meets every redistribution obligation. A production package must include the applicable licenses, notices, matching source access and required replacement/relinking materials. No commercial license is supplied or purchased. A free Android build remains separate and does not bundle this browser runtime.

User-selected font files remain in the short-lived renderer's memory. FileFlow does not redistribute Windows/Office fonts. The font owner controls their use and embedding permissions.
