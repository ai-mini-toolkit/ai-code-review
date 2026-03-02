import type { Page } from '@playwright/test';

/**
 * API mock helper for E2E tests — intercepts backend API calls via Playwright route().
 *
 * All API responses follow the backend's unified envelope:
 * { success: boolean, data: T, error: null, timestamp: string }
 *
 * @since 9.5.0
 */

const TIMESTAMP = '2026-02-23T12:00:00Z';

/** Wrap data in the standard API response envelope. */
function envelope<T>(data: T) {
  return { data, error: null, success: true, timestamp: TIMESTAMP };
}

/** Mock user data returned by auth endpoints. */
const MOCK_USER = {
  avatar: '',
  email: 'admin@aicodereview.com',
  enabled: true,
  homePath: '/dashboard',
  id: 1,
  realName: 'Admin User',
  role: 'ADMIN',
  roles: ['ADMIN'],
  username: 'admin',
};

/** Mock login response. */
const MOCK_LOGIN_RESULT = {
  accessToken: 'e2e-mock-jwt-token-for-testing',
  expiresIn: 3600,
  refreshToken: 'e2e-mock-refresh-token',
  tokenType: 'Bearer',
  user: MOCK_USER,
};

/** Mock project list. */
const MOCK_PROJECTS = [
  {
    createdAt: '2026-01-15T10:00:00Z',
    description: 'E2E test project',
    enabled: true,
    gitPlatform: 'github',
    id: 1,
    name: 'Test GitHub Project',
    repoUrl: 'https://github.com/test/repo',
    updatedAt: '2026-01-15T10:00:00Z',
  },
  {
    createdAt: '2026-01-20T10:00:00Z',
    description: 'GitLab test project',
    enabled: false,
    gitPlatform: 'gitlab',
    id: 2,
    name: 'Test GitLab Project',
    repoUrl: 'https://gitlab.com/test/repo',
    updatedAt: '2026-01-20T10:00:00Z',
  },
];

/** Mock AI model list. */
const MOCK_AI_MODELS = [
  {
    apiKeyConfigured: true,
    createdAt: '2026-01-10T10:00:00Z',
    enabled: true,
    id: 1,
    modelName: 'gpt-4',
    name: 'OpenAI GPT-4',
    providerType: 'OPENAI',
    updatedAt: '2026-01-10T10:00:00Z',
  },
];

/** Mock review task list. */
const MOCK_REVIEW_TASKS = [
  {
    author: 'developer1',
    branch: 'feature/auth',
    commitHash: 'abc123def456',
    completedAt: '2026-02-01T12:00:00Z',
    createdAt: '2026-02-01T10:00:00Z',
    id: 1,
    prNumber: 42,
    projectName: 'Test GitHub Project',
    repoUrl: 'https://github.com/test/repo',
    status: 'COMPLETED',
    taskType: 'PULL_REQUEST',
  },
];

/**
 * Sets up pre-login mocks: auth/me returns 401 (no session) so the app
 * shows the login page; login endpoint returns a valid JWT response.
 * Call this BEFORE navigating to the login page.
 */
export async function setupPreLoginMocks(page: Page) {
  // auth/me and auth/codes return 401 — no active session
  await page.route('**/api/v1/auth/me', (route) =>
    route.fulfill({
      body: JSON.stringify({ data: null, error: { code: 'UNAUTHORIZED', message: 'Not authenticated' }, success: false, timestamp: TIMESTAMP }),
      contentType: 'application/json',
      status: 401,
    }),
  );

  await page.route('**/api/v1/auth/codes', (route) =>
    route.fulfill({
      body: JSON.stringify({ data: null, error: { code: 'UNAUTHORIZED', message: 'Not authenticated' }, success: false, timestamp: TIMESTAMP }),
      contentType: 'application/json',
      status: 401,
    }),
  );

  // Login endpoint returns valid credentials
  await page.route('**/api/v1/auth/login', (route) =>
    route.fulfill({
      body: JSON.stringify(envelope(MOCK_LOGIN_RESULT)),
      contentType: 'application/json',
      status: 200,
    }),
  );

  // Catch-all for other API calls during pre-login phase
  await page.route('**/api/**', (route) => route.fulfill({
    body: JSON.stringify(envelope(null)),
    contentType: 'application/json',
    status: 200,
  }));
}

/**
 * Sets up all API route mocks for authenticated pages.
 * Call this AFTER login has completed (token stored by the app).
 */
export async function setupApiMocks(page: Page) {
  // Auth endpoints
  await page.route('**/api/v1/auth/login', (route) =>
    route.fulfill({
      body: JSON.stringify(envelope(MOCK_LOGIN_RESULT)),
      contentType: 'application/json',
      status: 200,
    }),
  );

  await page.route('**/api/v1/auth/me', (route) =>
    route.fulfill({
      body: JSON.stringify(envelope(MOCK_USER)),
      contentType: 'application/json',
      status: 200,
    }),
  );

  await page.route('**/api/v1/auth/codes', (route) =>
    route.fulfill({
      body: JSON.stringify(envelope(['ADMIN', 'USER'])),
      contentType: 'application/json',
      status: 200,
    }),
  );

  await page.route('**/api/v1/auth/refresh', (route) =>
    route.fulfill({
      body: JSON.stringify(envelope(MOCK_LOGIN_RESULT)),
      contentType: 'application/json',
      status: 200,
    }),
  );

  // Project endpoints
  await page.route('**/api/v1/projects?**', (route) =>
    route.fulfill({
      body: JSON.stringify(
        envelope({
          content: MOCK_PROJECTS,
          number: 0,
          size: 10,
          totalElements: MOCK_PROJECTS.length,
          totalPages: 1,
        }),
      ),
      contentType: 'application/json',
      status: 200,
    }),
  );

  await page.route('**/api/v1/projects', (route) => {
    if (route.request().method() === 'POST') {
      return route.fulfill({
        body: JSON.stringify(envelope({ ...MOCK_PROJECTS[0], id: 99 })),
        contentType: 'application/json',
        status: 201,
      });
    }
    return route.fulfill({
      body: JSON.stringify(
        envelope({
          content: MOCK_PROJECTS,
          number: 0,
          size: 10,
          totalElements: MOCK_PROJECTS.length,
          totalPages: 1,
        }),
      ),
      contentType: 'application/json',
      status: 200,
    });
  });

  // AI model endpoints
  await page.route('**/api/v1/ai-models**', (route) =>
    route.fulfill({
      body: JSON.stringify(
        envelope({
          content: MOCK_AI_MODELS,
          number: 0,
          size: 10,
          totalElements: MOCK_AI_MODELS.length,
          totalPages: 1,
        }),
      ),
      contentType: 'application/json',
      status: 200,
    }),
  );

  // Review task endpoints
  await page.route('**/api/v1/tasks**', (route) =>
    route.fulfill({
      body: JSON.stringify(
        envelope({
          content: MOCK_REVIEW_TASKS,
          number: 0,
          size: 10,
          totalElements: MOCK_REVIEW_TASKS.length,
          totalPages: 1,
        }),
      ),
      contentType: 'application/json',
      status: 200,
    }),
  );

  // Catch-all for any other API calls — return empty success
  await page.route('**/api/**', (route) => {
    if (!route.request().url().includes('/api/v1/auth/')) {
      return route.fulfill({
        body: JSON.stringify(envelope(null)),
        contentType: 'application/json',
        status: 200,
      });
    }
    return route.continue();
  });
}

/**
 * Sets up API mock that returns login failure.
 */
export async function setupLoginFailureMock(page: Page) {
  await page.route('**/api/v1/auth/login', (route) =>
    route.fulfill({
      body: JSON.stringify({
        data: null,
        error: { code: 'UNAUTHORIZED', message: 'Invalid credentials' },
        success: false,
        timestamp: TIMESTAMP,
      }),
      contentType: 'application/json',
      status: 401,
    }),
  );
}

export { MOCK_AI_MODELS, MOCK_LOGIN_RESULT, MOCK_PROJECTS, MOCK_REVIEW_TASKS, MOCK_USER };
