import type { Page } from '@playwright/test';

import { expect } from '@playwright/test';

/**
 * Page Object Model for the main dashboard / home page shown after login.
 *
 * Encapsulates selectors and assertions for the authenticated area,
 * allowing E2E tests to verify navigation, menu items, and core layout.
 *
 * @example
 * const dashboard = new DashboardPage(page);
 * await dashboard.expectVisible();
 * await dashboard.navigateTo('project');
 */
export class DashboardPage {
  readonly page: Page;

  constructor(page: Page) {
    this.page = page;
  }

  // ─── Locators ──────────────────────────────────────────────────────────────

  get sidebarMenu() {
    return this.page.locator('.el-menu, [class*="sidebar"], [class*="menu"]').first();
  }

  get userAvatar() {
    return this.page
      .locator('[class*="avatar"], [class*="user-info"], [class*="header-user"]')
      .first();
  }

  // ─── Assertions ────────────────────────────────────────────────────────────

  /** Asserts the authenticated dashboard is visible (sidebar present). */
  async expectVisible() {
    await expect(this.sidebarMenu).toBeVisible({ timeout: 10_000 });
  }

  /** Asserts the current URL does NOT contain the login path. */
  async expectAuthenticated() {
    await expect(this.page).not.toHaveURL(/auth\/login/);
  }

  // ─── Navigation ────────────────────────────────────────────────────────────

  /**
   * Clicks the sidebar menu item matching the given text pattern.
   *
   * @param menuText partial text or regex for the menu item
   */
  async navigateTo(menuText: string | RegExp) {
    const item = this.page.getByRole('menuitem', { name: menuText }).first();
    await item.click();
  }
}
