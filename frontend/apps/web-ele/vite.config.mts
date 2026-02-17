import { defineConfig } from '@vben/vite-config';

import ElementPlus from 'unplugin-element-plus/vite';

export default defineConfig(async () => {
  return {
    application: {},
    vite: {
      plugins: [
        ElementPlus({
          format: 'esm',
        }),
        // mermaid v11 uses jiti (Node.js module loader) internally.
        // jiti is not browser-compatible (uses Node built-ins + top-level await).
        // We stub it so the browser bundle only runs mermaid's browser code paths.
        {
          name: 'browser-compat-stubs',
          enforce: 'pre' as const,
          resolveId(id: string) {
            if (id === 'node:module') return '\0node:module-stub';
            if (id === 'jiti' || id.startsWith('jiti/')) return '\0jiti-stub';
          },
          load(id: string) {
            if (id === '\0node:module-stub') {
              return 'export const createRequire = () => () => ({}); export default { createRequire };';
            }
            if (id === '\0jiti-stub') {
              // Stub jiti for browser: mermaid uses it only for Node.js config loading
              const jitiStub = `
                const jiti = () => ({});
                jiti.import = async () => ({});
                export default jiti;
                export const createJiti = () => jiti;
              `;
              return jitiStub;
            }
          },
        },
      ],
      // mermaid v11 uses jiti which introduces top-level await.
      // Override build target to es2022+ to support top-level await in vendor chunks.
      // This app targets modern browsers (Chrome 94+, Firefox 93+, Safari 15+) so
      // targeting esnext is appropriate.
      build: {
        target: 'esnext',
      },
      // Prevent esbuild from pre-bundling mermaid (jiti is not browser-compatible
      // when pre-bundled separately; let Rollup handle it with the stub plugin above)
      optimizeDeps: {
        exclude: ['mermaid'],
      },
      server: {
        proxy: {
          '/api': {
            changeOrigin: true,
            // AI Code Review 后端 API
            target: 'http://localhost:8080',
            ws: true,
          },
        },
      },
    },
  };
});
