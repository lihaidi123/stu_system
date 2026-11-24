import { defineConfig } from 'vite'
import { resolve } from 'node:path'
import baseConfig from './vite.config.js'

export default defineConfig({
  ...baseConfig,
  build: {
    outDir: 'dist-new-workspace-preview',
    emptyOutDir: true,
    rollupOptions: {
      input: resolve(import.meta.dirname, 'new-workspace-preview.html'),
    },
  },
})
