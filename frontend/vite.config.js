import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// 开发时将 /api 代理到后端，避免浏览器 CORS；SSE 走同源代理可携带 Authorization
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8081',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, ''),
      },
    },
  },
})
