import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    // Dev only: the browser calls /api/... on the Vite origin, and Vite forwards it to the
    // gateway. Same origin from the browser's point of view, so no CORS (prod: Caddy does this).
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        // Send Host: localhost:8080 instead of localhost:5173.
        changeOrigin: true,
      },
    },
  },
})
