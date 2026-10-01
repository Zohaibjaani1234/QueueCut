import { defineConfig } from 'vite';

export default defineConfig({
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        secure: false,
        // The proxy makes /api same-origin, so drop the browser's Origin header.
        // Otherwise opening the dev server via 127.0.0.1 or a LAN IP (e.g. from a phone)
        // fails the backend's CORS allow-list with 403 "Invalid CORS request".
        configure: (proxy) => {
          proxy.on('proxyReq', (proxyReq) => {
            proxyReq.removeHeader('origin');
          });
        },
      },
    },
  },
});
