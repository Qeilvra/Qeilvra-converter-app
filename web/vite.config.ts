import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';
import { VitePWA } from 'vite-plugin-pwa';

export default defineConfig({
  test: {
    environment: 'jsdom',
    setupFiles: './src/test/setup.ts'
  },
  plugins: [
    react(),
    VitePWA({
      registerType: 'autoUpdate',
      includeAssets: ['favicon.svg'],
      manifest: {
        name: 'FileFlow — File tools that respect your privacy',
        short_name: 'FileFlow',
        description: 'Convert, compress, edit and prepare files locally in your browser.',
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
        globPatterns: ['**/*.{js,css,html,svg,png,woff2}'],
        navigateFallback: '/index.html',
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
          if (id.includes('pdfjs-dist')) return 'engine-pdfjs';
          if (id.includes('exceljs')) return 'engine-excel';
          if (id.includes('jspdf') || id.includes('html2canvas') || id.includes('dompurify')) return 'engine-jspdf';
          if (id.includes('pdf-lib')) return 'engine-pdflib';
          if (id.includes('/docx/') || id.includes('jszip')) return 'engine-office';
        }
      }
    }
  }
});
