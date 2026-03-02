import { test } from '@playwright/test';

import { LoginPage } from './pages/LoginPage';

/**
 * Project management E2E tests — Story 9.5 AC#2.
 *
 * NOTE: Full CRUD tests require a running backend (Vben Admin's dynamic route
 * generation depends on auth store state that can't be fully replicated via
 * API mocks). These tests verify the login → redirect flow that precedes
 * project management. Full CRUD testing is deferred to CI integration (Story 9.6).
 */
test.describe('Project Management', () => {
  test('AC#2 — unauthenticated access to /project redirects to login', async ({ page }) => {
    await page.goto('/project');
    await page.waitForLoadState('domcontentloaded');

    // Should redirect to login page (auth guard)
    const loginPage = new LoginPage(page);
    await loginPage.expectLoginPageVisible();
  });
});
