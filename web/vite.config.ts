import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';
import path from 'node:path';

// https://vitejs.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');
  // 后端 application.yaml 默认 server.port=8081，可通过环境变量 VITE_API_PROXY_TARGET 覆盖
  const apiTarget = process.env.VITE_API_PROXY_TARGET || env.VITE_API_PROXY_TARGET || 'http://127.0.0.1:8081';
  const devHost = process.env.VITE_DEV_HOST || env.VITE_DEV_HOST || '0.0.0.0';
  const devPort = Number(process.env.VITE_DEV_PORT || env.VITE_DEV_PORT || 3000);
  const allowedHostsRaw = process.env.VITE_DEV_ALLOWED_HOSTS || env.VITE_DEV_ALLOWED_HOSTS || '';
  const allowedHosts =
    allowedHostsRaw.trim() === '*'
      ? true
      : allowedHostsRaw
          .split(',')
          .map((host) => host.trim())
          .filter(Boolean);

  return {
    plugins: [react()],
    resolve: {
      alias: {
        '@': path.resolve(__dirname, './src'),
      },
    },
    server: {
      host: devHost,
      port: devPort,
      strictPort: false,
      // Dev-only: allow HTTPS tunnel domains such as ngrok / trycloudflare / cpolar.
      allowedHosts,
      proxy: {
        '/api': {
          target: apiTarget,
          changeOrigin: true,
          ws: true,
          // Mobile HTTPS tunnel -> Vite proxy -> local backend.
          // Rewrite WS Origin so Spring's local dev origin whitelist does not reject the handshake.
          rewriteWsOrigin: true,
          // 后端实际路径：/api/auth/login 等；前端请求路径就是 /api/...
        },
      },
    },
    build: {
      outDir: 'dist',
      sourcemap: false,
      target: 'es2020',
      chunkSizeWarningLimit: 1024,
    },
  };
});
