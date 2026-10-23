FileFlow's office renderer uses a locally hosted ZetaOffice / LibreOffice WebAssembly runtime. Generated files are excluded from Git and prepared by the production build. The application never loads a vendor CDN and never submits documents to one. The build downloads public engine software only.

ZetaJS: Copyright (c) 2024 allotropia software GmbH and contributors. MIT license. Source and full license: https://github.com/allotropia/zetajs (the reviewed source revision is recorded in web/scripts/office-runtime-lock.json).

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

LibreOffice is available under MPL 2.0 and includes third-party works with other licenses. ZetaOffice's documented runtime source references are:

- https://git.libreoffice.org/core/+/refs/heads/distro/allotropia/zeta-24-2
- https://github.com/allotropia/emscripten/commits/fixed-3.1.65
- https://github.com/allotropia/qt5/tree/5.15.2%2Bwasm
- https://github.com/allotropia/qtbase/tree/5.15.2%2Bwasm
- https://www.libreoffice.org/licenses/

The runtime's embedded versionrc reports LibreOffice build ID efaf0670b4d055f838a2849becb10f08aa06a257. The upstream source pointers above are not a verified, complete corresponding-source package for that binary. In particular, Qt 5.15's WebAssembly platform plugin is GPLv3 or commercial, not simply LGPL: https://doc.qt.io/archives/qt-5.15/wasm.html#licenses . The referenced allotropia qtbase branch's src/plugins/platforms/wasm/qwasmwindow.cpp also declares GPLv3-or-later/commercial terms. Do not infer that ZetaJS's MIT license covers the entire engine.

Build packaging and local conversion are verified. The CDN package's complete matching source, build instructions and third-party license materials have not been assembled into a release. Public redistribution still needs those materials and compliance with the applicable GPL/MPL and other component terms; the notice alone does not complete that work. No commercial license is supplied or purchased. This is a remaining public-release blocker, separate from the technical Vercel packaging fix. A free Android build remains separate and does not bundle this browser runtime.

User-selected font files remain in the short-lived renderer's memory. FileFlow does not redistribute Windows/Office fonts. The font owner controls their use and embedding permissions.
