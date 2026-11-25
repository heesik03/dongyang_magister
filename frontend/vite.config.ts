import react from '@vitejs/plugin-react'
import { defineConfig, loadEnv } from 'vite'

export function resolveConnectSrc(mode: string, rawApiUrl: string | undefined): string {
  const sources = new Set<string>(["'self'"])
  if (mode === 'development') {
    sources.add('http://localhost:8080')
    sources.add('http://127.0.0.1:8080')
  }
  const trimmed = rawApiUrl?.trim() ?? ''
  if (trimmed) {
    let parsed: URL
    try {
      parsed = new URL(trimmed)
    } catch {
      throw new Error(`VITE_API_URL must be a valid origin URL (received: ${trimmed})`)
    }
    if (parsed.protocol !== 'http:' && parsed.protocol !== 'https:') {
      throw new Error(`VITE_API_URL must use http or https protocol (received: ${trimmed})`)
    }
    if ((parsed.pathname && parsed.pathname !== '/') || parsed.search || parsed.hash) {
      throw new Error(
        `VITE_API_URL must be an origin without '/api' path, query, or hash (received: ${trimmed})`,
      )
    }
    sources.add(parsed.origin)
  }
  return `connect-src ${Array.from(sources).join(' ')}`
}

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const connectSrc = resolveConnectSrc(mode, env.VITE_API_URL ?? process.env.VITE_API_URL)
  return {
    plugins: [
      react(),
      {
        name: 'magister-csp-connect-src',
        transformIndexHtml(html) {
          return html.replace(/connect-src\s+[^;]+/, connectSrc)
        },
      },
    ],
    server: { proxy: { '/api': 'http://127.0.0.1:8080' } },
  }
})
