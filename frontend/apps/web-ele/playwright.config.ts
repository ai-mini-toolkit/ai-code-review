import type { PlaywrightTestConfig } from '@playwright/test';

import { devices } from '@playwright/test';

/**
 * Playwright E2E test configuration for the web-ele application.
 *
 * Tests are located in ./e2e/ and run against the local dev server on port 5173.
 *
 * Run tests:
 *   pnpm test:e2e          — headless (CI mode)
 *   pnpm test:e2e-ui       — interactive UI mode
 *
 * @see https://playwright.dev/docs/test-configuration
 */
const config: PlaywrightTestConfig = {
  testDir: './e2e',

  /* Maximum time one test can run */
  timeout: 30_000,

  expect: {
    /* Maximum time expect() should wait for the condition to be met */
    timeout: 5_000,
  },

  /* Fail the build on CI if test.only is accidentally left in */
  forbidOnly: !!process.env.CI,

  /* Retry on CI only */
  retries: process.env.CI ? 2 : 0,

  /* Opt out of parallel tests on CI */
  workers: process.env.CI ? 1 : undefined,

  reporter: [
    ['list'],
    ['html', { outputFolder: 'node_modules/.e2e/test-results' }],
  ],

  outputDir: 'node_modules/.e2e/test-results/',

  use: {
    /* Base URL for all page.goto() calls */
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:5173',

    /* Collect trace on retry for debugging */
    trace: 'retain-on-failure',

    /* Only headless on CI */
    headless: !!process.env.CI,

    actionTimeout: 0,
  },

  projects: [
    {
      name: 'chromium',
      use: {
        ...devices['Desktop Chrome'],
      },
    },
    // Uncomment to add more browsers:
    // {
    //   name: 'firefox',
    //   use: { ...devices['Desktop Firefox'] },
    // },
  ],

  /* Start the dev server before running tests.
   * reuseExistingServer: true — reuses a running server in both local and CI environments.
   * In CI: pre-start the dev server or point E2E_BASE_URL to the deployed app. */
  webServer: {
    command: 'pnpm dev',
    port: 5173,
    reuseExistingServer: true,
    timeout: 120_000,
  },
};

export default config;
