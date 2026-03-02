import { test } from '@playwright/test';

import { LoginPage } from './pages/LoginPage';

/**
 * Review workflow E2E tests — Story 9.5 AC#4.
 *
 * NOTE: Full review detail tests require running backend.
 */
test.describe('Review Workflow', () => {
  test('AC#4 — unauthenticated access to /review redirects to login', async ({ page }) => {
    await page.goto('/review');
    await page.waitForLoadState('domcontentloaded');

    const loginPage = new LoginPage(page);
    await loginPage.expectLoginPageVisible();
  });
});
