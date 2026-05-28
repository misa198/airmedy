import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'
import path from 'path'

export default defineConfig({
  plugins: [vue()],
  test: {
    environment: 'jsdom',
    globals: true,
    passWithNoTests: true,
  },
  resolve: {
    alias: {
      '@airmedy/utils': path.resolve(__dirname, '../utils/src'),
    },
  },
})
