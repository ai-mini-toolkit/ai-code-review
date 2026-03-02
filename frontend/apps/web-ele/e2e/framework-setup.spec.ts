import { expect, test } from '@playwright/test';

import { DashboardPage } from './pages/DashboardPage';
import { LoginPage } from './pages/LoginPage';

/**
 * E2E framework validation tests — Story 9.1 AC #5.
 *
 * Verifies that:
 * 1. Playwright can launch Chromium and reach the application
 * 2. The login page renders key elements (Page Object Model works)
 * 3. LoginPage and DashboardPage POMs function correctly
 *
 * These tests do NOT perform a full login flow (which requires a running
 * backend). They validate the testing infrastructure itself.
 */
test.describe('E2E Framework Setup Validation', () => {
  test('application is reachable and returns a page title', async ({ page }) => {
    await page.goto('/');

    const title = await page.title();
    expect(title).toBeTruthy();
    expect(title.length).toBeGreaterThan(0);
  });

  test('login page renders without JavaScript errors', async ({ page }) => {
    const errors: string[] = [];
    page.on('pageerror', (err) => errors.push(err.message));

    await page.goto('/');
    await page.waitForLoadState('domcontentloaded');

    expect(errors).toHaveLength(0);
  });

  test('LoginPage POM — login button is visible', async ({ page }) => {
    const loginPage = new LoginPage(page);
    await loginPage.goto();

    await loginPage.expectLoginPageVisible();
  });

  test('LoginPage POM — username and password inputs are present', async ({ page }) => {
    const loginPage = new LoginPage(page);
    await loginPage.goto();

    await expect(loginPage.usernameInput).toBeVisible();
    await expect(loginPage.passwordInput).toBeVisible();
  });

  test('DashboardPage POM — import resolves without error', async ({ page }) => {
    // Import resolution is validated at compile time; this test confirms
    // DashboardPage can be instantiated and its locators accessed without throwing.
    const dashboard = new DashboardPage(page);
    expect(dashboard).toBeDefined();
    expect(dashboard.sidebarMenu).toBeDefined();
    expect(dashboard.userAvatar).toBeDefined();
  });

  test('Playwright config — baseURL is configured', async ({ page }) => {
    // Navigating to '/' uses the configured baseURL from playwright.config.ts
    const response = await page.goto('/');
    expect(response).not.toBeNull();
    expect(response!.status()).toBeLessThan(500);
  });
});
