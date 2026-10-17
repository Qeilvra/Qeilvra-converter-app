# Deployment

## Web

1. Run `npm ci` in `web/`.
2. Run `npm run typecheck`, `npm test`, and `npm run build`.
3. Deploy `web/dist` to an HTTPS static host with SPA fallback to `index.html`.
4. Set security headers at the host: a restrictive Content Security Policy, `X-Content-Type-Options: nosniff`, `Referrer-Policy: strict-origin-when-cross-origin`, and appropriate cross-origin policies after verifying PDF workers.

No environment variables or secrets are required. Public deployment was intentionally not performed because the project brief requires explicit authorization.

## Android

The current machine does not expose Gradle, Android SDK or ADB. On an Android development machine, install Android SDK 36 and Build Tools 36.0.0, define `local.properties`, use Gradle 9.6 with the checked-in AGP 9.4 configuration, build a debug variant, run unit/instrumentation tests, then verify file pick/save/share and memory behavior on a physical low-memory device. Release signing credentials must be supplied by the owner and must not be committed.
