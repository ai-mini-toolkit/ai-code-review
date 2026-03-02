import type { Page } from '@playwright/test';

import { expect } from '@playwright/test';

/**
 * Page Object Model for the login page.
 *
 * Encapsulates selectors and actions for the authentication screen,
 * keeping test code decoupled from implementation details.
 *
 * @example
 * const login = new LoginPage(page);
 * await login.goto();
 * await login.loginWith('admin', '123456');
 * await login.expectLoginSuccess();
 */
export class LoginPage {
  readonly page: Page;

  constructor(page: Page) {
    this.page = page;
  }

  // ─── Navigation ────────────────────────────────────────────────────────────

  async goto() {
    await this.page.goto('/');
  }

  // ─── Locators ──────────────────────────────────────────────────────────────

  get usernameInput() {
    return this.page.getByPlaceholder(/username|用户名/i).first();
  }

  get passwordInput() {
    return this.page.getByPlaceholder(/password|密码/i).first();
  }

  get loginButton() {
    return this.page.getByRole('button', { name: /login|登录/i }).first();
  }

  get errorMessage() {
    return this.page.locator('.el-message--error, [class*="error"]').first();
  }

  // ─── Actions ───────────────────────────────────────────────────────────────

  /**
   * Fills in credentials and submits the login form.
   *
   * @param username login username
   * @param password login password
   */
  async loginWith(username: string, password: string) {
    await this.usernameInput.fill(username);
    await this.passwordInput.fill(password);
    await this.loginButton.click();
  }

  // ─── Assertions ────────────────────────────────────────────────────────────

  /** Asserts the login page is currently visible. */
  async expectLoginPageVisible() {
    await expect(this.loginButton).toBeVisible();
  }

  /** Asserts successful login by checking navigation away from the login page. */
  async expectLoginSuccess() {
    await expect(this.page).not.toHaveURL(/auth\/login/);
  }

  /** Asserts an error message is shown after failed login. */
  async expectLoginError() {
    await expect(this.errorMessage).toBeVisible();
  }
}
