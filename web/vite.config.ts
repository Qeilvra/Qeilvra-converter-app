import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';
import { VitePWA } from 'vite-plugin-pwa';
import { statSync } from 'node:fs';
import { resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

// Match the production asset contract in dev/preview too: a missing engine
// file is a 404, never the SPA HTML shell with a misleading 200 response.
const webRoot=fileURLToPath(new URL('.',import.meta.url));
const officeAssets=(root:string) => (request:{url?:string},response:{statusCode:number;setHeader(name:string,value:string):void;end(value:string):void},next:()=>void) => {
  const path=new URL(request.url || '/','http://localhost').pathname;
  if(!path.startsWith('/office-runtime/') && !path.startsWith('/office-renderer/')){next();return}
  try {
    const file=resolve(root,'.'+decodeURIComponent(path));
    if(!file.startsWith(root+sep) || !statSync(file).isFile())throw Error('Missing asset');
    if(path.endsWith('.gzip'))response.setHeader('Content-Type','application/gzip');
    if(path.endsWith('manifest.json') || path.endsWith('.metadata'))response.setHeader('Content-Type','application/json; charset=utf-8');
    next();
  }catch{response.statusCode=404;response.setHeader('Content-Type','text/plain; charset=utf-8');response.end('Office runtime asset not found.');}
};

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
    {name:'office-static-assets',configureServer(server){server.middlewares.use(officeAssets(resolve(webRoot,'public')))},configurePreviewServer(server){server.middlewares.use(officeAssets(resolve(webRoot,'dist')))}},
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
        globIgnores: ['office-runtime/**','office-renderer/office-thread.js'],
        navigateFallback: '/index.html',
        navigateFallbackDenylist: [/^\/api\//, /^\/office-runtime\//, /^\/office-renderer\//],
        // Runtime files are fetched from this same origin only. Once used, the large
        // office engine can work offline; it is never downloaded from a vendor CDN.
        runtimeCaching: [
          { urlPattern: ({url}) => url.pathname==='/office-runtime/manifest.json', handler:'NetworkFirst', options:{cacheName:'fileflow-office-manifest-v2',networkTimeoutSeconds:5,cacheableResponse:{statuses:[200]}} },
          { urlPattern: ({url}) => /^\/office-runtime\/v-[a-f0-9]{16}\//.test(url.pathname), handler:'CacheFirst', options: { cacheName:'fileflow-office-runtime-v2', expiration:{maxEntries:16}, cacheableResponse:{statuses:[200]} } }
        ],
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
