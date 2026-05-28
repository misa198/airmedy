import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'
import path from 'path'

export default defineConfig({
  plugins: [vue()],
  test: {
    environment: 'jsdom',
    globals: true,
  },
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
      '@airmedy/ui': path.resolve(__dirname, '../packages/ui/src'),
      '@airmedy/utils': path.resolve(__dirname, '../packages/utils/src'),
    },
  },
})
