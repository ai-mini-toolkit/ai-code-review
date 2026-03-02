import { expect, test } from '@playwright/test';

import { LoginPage } from './pages/LoginPage';

/**
 * Page health E2E tests — Story 9.5 AC#5.
 *
 * Verifies core pages load without JavaScript console errors.
 */
test.describe('Page Health & Navigation', () => {
  test('AC#5 — login page loads without JavaScript errors', async ({ page }) => {
    const errors: string[] = [];
    page.on('pageerror', (err) => errors.push(err.message));

    await page.goto('/');
    await page.waitForLoadState('domcontentloaded');

    expect(errors).toHaveLength(0);
  });

  test('AC#5 — login form elements are interactive', async ({ page }) => {
    const loginPage = new LoginPage(page);
    await loginPage.goto();

    // Verify all login form elements exist and are interactive
    await expect(loginPage.usernameInput).toBeVisible();
    await expect(loginPage.passwordInput).toBeVisible();
    await expect(loginPage.loginButton).toBeVisible();

    // Can type into inputs
    await loginPage.usernameInput.fill('test-user');
    await expect(loginPage.usernameInput).toHaveValue('test-user');
  });

  test('AC#5 — protected routes redirect to login (auth guard works)', async ({ page }) => {
    const protectedRoutes = ['/project', '/ai-model', '/review'];

    for (const route of protectedRoutes) {
      await page.goto(route);
      await page.waitForLoadState('domcontentloaded');

      // Auth guard should redirect unauthenticated users to login
      const loginPage = new LoginPage(page);
      await loginPage.expectLoginPageVisible();
    }
  });
});
