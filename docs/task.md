# Delivery checklist

## Implemented in current milestone

- [x] Monorepo and release documentation
- [x] Responsive FileFlow dashboard matching the supplied visual direction
- [x] Functional Home, Tools, Recent, Settings, Privacy, Terms and Help routes
- [x] File signature and size validation
- [x] Image format conversion with transparency disclosure
- [x] Measured image target-size compression
- [x] Multipage image-to-PDF with reorder and rotation
- [x] PDF merge, page reorder, delete/extract and rotation
- [x] Structural PDF optimization with honest measured result
- [x] Prepare for Upload with editable presets and verification
- [x] PDF table extraction, editable review and valid XLSX output
- [x] Text-focused valid DOCX and PDF conversion paths with up-front limitations
- [x] Browser download/share and metadata-only recent history
- [x] PWA manifest and asset caching
- [x] Dependency license inventory and zero-known-vulnerability npm audit

## Next release work

- [ ] OCR for scanned PDFs with a measured low-memory strategy
- [ ] Automatic scanner edge detection, perspective correction and manual crop UI
- [ ] High-fidelity Office conversion through an explicitly consented isolated service
- [ ] Visual PDF thumbnails in the page organizer
- [ ] Android parity for every web workflow
- [ ] Physical-device, camera and share-sheet testing
- [ ] Performance profiling with large real-world documents

## Verification record — 2026-10-16

- Web strict TypeScript check: passed.
- Web Vitest suite: 5/5 passed, including parser verification of generated XLSX/PDF outputs.
- Web production/PWA build: passed; 31 offline assets precached and heavy engines code-split.
- npm production/development dependency audit: zero known vulnerabilities.
- Local HTTP smoke test: Home and deep-linked PDF-to-Excel route both returned HTTP 200.
- Gradle 9.6 wrapper execution: passed.
- Android Gradle project configuration (`tasks`): passed with AGP 9.4.
- Android `assembleDebug`: passed after using the installed Android SDK 36 and Build Tools 36.1.0. The generated debug APK was validated with `aapt2`.
- Android install/device launch: not run because ADB reports no connected devices and this SDK installation has no emulator component.
- Android XML resources: all parsed as well-formed XML.
