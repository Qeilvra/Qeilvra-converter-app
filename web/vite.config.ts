import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';
import { VitePWA } from 'vite-plugin-pwa';

export default defineConfig({
  server: { headers: { 'Cross-Origin-Opener-Policy':'same-origin', 'Cross-Origin-Embedder-Policy':'require-corp' }, proxy: { '/api/v1': {
    target: 'http://127.0.0.1:8080', changeOrigin: true,
    configure(proxy) {
      proxy.on('proxyReq', (proxied, incoming) => {
        // The browser already called its own origin. Avoid a false CORS mismatch after
        // proxying Host to the worker; preserve foreign origins for backend rejection.
        const origin = incoming.headers.origin;
        try { if (origin && new URL(origin).host === incoming.headers.host) proxied.removeHeader('origin'); }
        catch { /* Malformed origins must reach the backend's CORS rejection. */ }
      });
    }
  } } },
  preview: { headers: { 'Cross-Origin-Opener-Policy':'same-origin', 'Cross-Origin-Embedder-Policy':'require-corp' } },
  test: {
    environment: 'jsdom',
    setupFiles: './src/test/setup.ts',
    exclude: ['e2e/**', 'node_modules/**', 'dist/**']
  },
  plugins: [
    react(),
    VitePWA({
      registerType: 'autoUpdate',
      includeAssets: ['favicon.svg'],
      manifest: {
        name: 'FileFlow — File tools that respect your privacy',
        short_name: 'FileFlow',
        description: 'Local document conversion, image tools and PDF editing. No account required.',
        theme_color: '#f2f7ff',
        background_color: '#f2f7ff',
        display: 'standalone',
        start_url: '/',
        icons: [
          { src: '/icon-192.svg', sizes: '192x192', type: 'image/svg+xml' },
          { src: '/icon-512.svg', sizes: '512x512', type: 'image/svg+xml' }
        ]
      },
      workbox: {
        globPatterns: ['**/*.{js,mjs,css,html,svg,png,woff2,txt}'],
        globIgnores: ['office-runtime/**'],
        navigateFallback: '/index.html',
        navigateFallbackDenylist: [/^\/api\//, /^\/office-runtime\//, /^\/office-renderer\//],
        // Runtime files are fetched from this same origin only. Once used, the large
        // office engine can work offline; it is never downloaded from a vendor CDN.
        runtimeCaching: [{ urlPattern: ({url}) => url.pathname.startsWith('/office-runtime/'), handler:'CacheFirst', options: { cacheName:'fileflow-office-runtime-v1', expiration:{maxEntries:8}, cacheableResponse:{statuses:[200]} } }],
        maximumFileSizeToCacheInBytes: 3 * 1024 * 1024
      }
    })
  ],
  build: {
    target: 'es2022',
    sourcemap: false,
    chunkSizeWarningLimit: 1500,
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (id.includes('jspdf') || id.includes('html2canvas') || id.includes('dompurify')) return 'engine-jspdf';
          if (id.includes('pdf-lib')) return 'engine-pdflib';
          if (id.includes('/docx/') || id.includes('jszip')) return 'engine-office';
        }
      }
    }
  }
});
