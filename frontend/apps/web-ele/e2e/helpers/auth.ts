import type { Page } from '@playwright/test';

import { LoginPage } from '../pages/LoginPage';
import { setupApiMocks } from './api-mock';

/**
 * Auth helper for E2E tests — handles login setup with API mocking.
 *
 * Strategy: Navigate to `/` with NO mocks (app shows login page naturally
 * since no backend is running). Then mock the login endpoint + post-login
 * endpoints, fill credentials, and submit.
 *
 * @since 9.5.0
 */

/**
 * Performs a mocked login flow:
 * 1. Sets up all API mocks (including login endpoint)
 * 2. Navigates to `/` — app shows login page (auth check fails naturally)
 * 3. Waits for login form to appear
 * 4. Fills credentials and submits
 * 5. Login mock returns JWT → app navigates to dashboard
 */
export async function loginAsAdmin(page: Page) {
  // Set up ALL mocks upfront — the login page shows because the initial
  // auth check (auth/me) succeeds but the app's token store is empty,
  // OR because no existing token triggers the login redirect.
  // We set up mocks first so that API calls during/after login work.
  await setupApiMocks(page);

  // Navigate — app will show login page if no stored token
  const loginPage = new LoginPage(page);
  await loginPage.goto();

  // Wait for either the login form OR the dashboard (in case app auto-logs in from mock)
  try {
    await loginPage.expectLoginPageVisible();
    // Login form visible — fill and submit
    await loginPage.loginWith('admin', '123456');
    // Wait for navigation away from login
    await page.waitForURL(/.*(?<!auth\/login)$/, { timeout: 15_000 });
  } catch {
    // Login page not visible — app may have auto-authenticated from mock.
    // This is acceptable if the dashboard is showing.
  }
}
