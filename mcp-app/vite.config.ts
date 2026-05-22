import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { viteSingleFile } from 'vite-plugin-singlefile'
import { resolve } from 'path'
import { fileURLToPath } from 'url'

const __dirname = fileURLToPath(new URL('.', import.meta.url))

// F-21 (bundle size measurement) — last measured 2026-05-22:
//   dist/index.html = 584,747 bytes (~571 KB, gzip 159 KB), built against ext-apps 1.7.2.
//   Threshold per audit: > 200 KB warrants documentation.
//   Source of size: React 19 + react-dom + marked + ext-apps + all CSS inlined via
//   vite-plugin-singlefile (viteSingleFile + cssCodeSplit:false + assetsInlineLimit).
//   This is acceptable today — the resource is served via `ui://jira/issue-card@{hash}`
//   so clients can cache by content hash. Splitting JS from HTML is deferred to a
//   future wave (would require multi-resource read or external script loading, which
//   conflicts with the sandboxed-iframe model MCP Apps clients expect).

export default defineConfig({
  plugins: [react(), viteSingleFile()],
  root: resolve(__dirname, 'src/issue-card'),
  build: {
    outDir: resolve(__dirname, 'dist'),
    emptyOutDir: true,
    assetsInlineLimit: 100000000,
    cssCodeSplit: false,
    target: 'es2020',
    rollupOptions: {
      input: resolve(__dirname, 'src/issue-card/index.html'),
      output: {
        inlineDynamicImports: true,
      },
    },
  },
})
