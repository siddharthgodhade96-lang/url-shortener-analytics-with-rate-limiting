import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// In development the browser talks to Vite (port 5173) and Vite forwards /api
// calls to the Spring Boot backend (port 8080). Because the browser only ever
// talks to one address, you do not run into CORS problems.
const BACKEND = process.env.BACKEND_URL || 'http://localhost:8080';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    strictPort: true,
    proxy: {
      '/api': {
        target: BACKEND,
        changeOrigin: false,
        configure: (proxy) => {
          // The backend only accepts browser calls from its configured origin.
          // Removing Origin makes proxied calls look like normal server calls,
          // so it also works if you open http://127.0.0.1:5173 instead of localhost.
          proxy.on('proxyReq', (proxyReq) => proxyReq.removeHeader('origin'));
        },
      },
    },
  },
});
