import { test } from '@playwright/test';

import { LoginPage } from './pages/LoginPage';

/**
 * AI model management E2E tests — Story 9.5 AC#3.
 *
 * NOTE: Full CRUD tests require running backend for Vben Admin's dynamic route
 * generation. These tests verify the auth guard redirect behavior.
 */
test.describe('AI Model Management', () => {
  test('AC#3 — unauthenticated access to /ai-model redirects to login', async ({ page }) => {
    await page.goto('/ai-model');
    await page.waitForLoadState('domcontentloaded');

    const loginPage = new LoginPage(page);
    await loginPage.expectLoginPageVisible();
  });
});
