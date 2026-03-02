import { defineConfig } from '@vben/vite-config';

export default defineConfig(async () => {
  return {
    application: {},
    vite: {
      server: {
        proxy: {
          '/api': {
            changeOrigin: true,
            // 代理到真实后端
            target: 'http://localhost:8080',
            ws: true,
          },
        },
      },
    },
  };
});
