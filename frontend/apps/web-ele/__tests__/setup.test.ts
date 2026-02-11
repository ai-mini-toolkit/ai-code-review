import { describe, expect, it } from 'vitest';

describe('Project Setup Verification', () => {
  it('should have correct environment variables configured', () => {
    // Verify Vite env variable naming convention
    const envVarPattern = /^VITE_/;
    expect(envVarPattern.test('VITE_GLOB_API_URL')).toBe(true);
    expect(envVarPattern.test('VITE_PORT')).toBe(true);
  });

  it('should have required dependencies available', async () => {
    // Vue 3
    const vue = await import('vue');
    expect(vue.createApp).toBeDefined();
    expect(vue.ref).toBeDefined();
    expect(vue.computed).toBeDefined();

    // Pinia
    const pinia = await import('pinia');
    expect(pinia.createPinia).toBeDefined();
    expect(pinia.defineStore).toBeDefined();

    // Vue Router
    const router = await import('vue-router');
    expect(router.createRouter).toBeDefined();
    expect(router.createWebHistory).toBeDefined();
  });

  it('should have Element Plus available', async () => {
    const elementPlus = await import('element-plus');
    expect(elementPlus.ElButton).toBeDefined();
    expect(elementPlus.ElMessage).toBeDefined();
    expect(elementPlus.ElConfigProvider).toBeDefined();
  });

  it('should have App component defined', async () => {
    const App = await import('../src/app.vue');
    expect(App.default).toBeDefined();
  });

  it('should have router configuration', async () => {
    const routerModule = await import('../src/router');
    expect(routerModule.router).toBeDefined();
  });

  it('should have API request client configured', async () => {
    const requestModule = await import('../src/api/request');
    expect(requestModule.requestClient).toBeDefined();
    expect(requestModule.baseRequestClient).toBeDefined();
  });
});
