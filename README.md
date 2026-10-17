# FileFlow

FileFlow is a privacy-first file utility for the web and Android. The web app performs its current conversion and compression workflows locally in the browser; no document is uploaded.

## Workspace

- `web/` — React, TypeScript, Vite, browser-local file tools and PWA shell
- `android/` — native Java/XML Android foundation (SDK build pending on a machine with Android tooling)
- `docs/` — product, architecture, privacy, security and release notes
- `test-fixtures/` — small, non-sensitive fixtures used by automated tests

## Run the web app

```bash
cd web
npm install
npm run dev
```

Quality checks:

```bash
npm run typecheck
npm test
npm run build
```

See `docs/architecture.md` for capability boundaries and `docs/task.md` for current delivery status.

## Android

The native project targets API 36 with Java/XML and AGP 9.4. On a machine with Android SDK 36 installed:

```powershell
cd android
.\gradlew.bat assembleDebug
```

The current native milestone exposes only the image compressor because the remaining Android tools have not yet passed their release gates. See `docs/task.md` for the verified boundary.
