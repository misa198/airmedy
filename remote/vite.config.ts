import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'path'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
      '@airmedy/ui': path.resolve(__dirname, '../packages/ui/src'),
      '@airmedy/utils': path.resolve(__dirname, '../packages/utils/src'),
    },
  },
  base: '/',
  build: {
    outDir: 'dist',
    emptyOutDir: true,
  },
})
