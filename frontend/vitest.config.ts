import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  test: { maxWorkers: 2, include: ['tests/**/*.test.{ts,tsx,mjs}'] },
})
