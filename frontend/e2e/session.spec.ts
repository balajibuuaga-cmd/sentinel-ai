import { test, expect, type Page } from '@playwright/test';

// Seeded demo accounts from the V5 migration.
const ADMIN = { email: 'admin@sentinel.ai', password: 'sentinel-admin' };
const VIEWER = { email: 'viewer@sentinel.ai', password: 'sentinel-viewer' };

const TOKEN_KEY = 'sentinel-ai-token';
const BACKEND_ERROR = 'Could not reach the Sentinel AI backend';

// A structurally valid JWT with a bogus signature. This shape matters: the
// client decodes the payload locally to build the session, so a malformed token
// is rejected before any request is made and never exercises server rejection.
// Only a decodable-but-unverifiable token reaches the backend — which is the
// real "my session expired while I was away" case.
const EXPIRED_SESSION_SCRIPT = ([key]: string[]) => {
  const b64url = (value: object) =>
    btoa(JSON.stringify(value)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  const header = b64url({ alg: 'HS256', typ: 'JWT' });
  const payload = b64url({
    sub: 'admin@sentinel.ai',
    role: 'ADMIN',
    tenantId: 'sentinel-demo',
    organizationName: 'Sentinel Demo',
    exp: 1,
  });
  window.localStorage.setItem(key, `${header}.${payload}.not-a-real-signature`);
};

async function signIn(page: Page, account: { email: string; password: string }) {
  await page.goto('/login');
  await page.getByLabel('Email').fill(account.email);
  await page.getByLabel('Password').fill(account.password);
  await page.getByRole('button', { name: 'Sign In' }).click();
  await expect(page.getByRole('link', { name: 'Command Center' })).toBeVisible({ timeout: 20_000 });
}

test.describe('session lifecycle', () => {
  test.beforeEach(async ({ page }) => {
    await page.addInitScript(() => window.localStorage.clear());
  });

  // Regression: the backend answered unauthenticated requests with 403, which the
  // console did not treat as "session gone" (it only handled 401). The result was
  // a "Could not reach the Sentinel AI backend" error screen on every expired
  // session instead of a redirect to login.
  test('a server-rejected session redirects to login rather than showing a backend error', async ({ page }) => {
    await page.addInitScript(EXPIRED_SESSION_SCRIPT, [TOKEN_KEY]);

    await page.goto('/');

    await expect(page).toHaveURL(/\/login$/, { timeout: 20_000 });
    await expect(page.getByText(BACKEND_ERROR)).toHaveCount(0);
  });

  test('a server-rejected session is cleared from storage so it cannot loop', async ({ page }) => {
    await page.addInitScript(EXPIRED_SESSION_SCRIPT, [TOKEN_KEY]);

    await page.goto('/');
    await expect(page).toHaveURL(/\/login$/, { timeout: 20_000 });

    const stored = await page.evaluate((key) => window.localStorage.getItem(key), TOKEN_KEY);
    expect(stored).toBeNull();
  });

  // Regression: useDashboard fetched /api/operator/console for every role, but
  // that route is ADMIN/RELEASE_MANAGER only, so a VIEWER's 403 failed the entire
  // dashboard. Signing in as a viewer must still render the console.
  test('a VIEWER reaches the dashboard even though the operator console is forbidden', async ({ page }) => {
    await signIn(page, VIEWER);

    await expect(page.getByText(BACKEND_ERROR)).toHaveCount(0);
    await expect(page).not.toHaveURL(/\/login$/);
  });

  test('logging out returns to login and re-protects routes', async ({ page }) => {
    await signIn(page, ADMIN);

    await page.getByTitle('Log out').click();
    await expect(page).toHaveURL(/\/login$/, { timeout: 20_000 });

    // Going back to a protected route must not restore the old session.
    await page.goto('/');
    await expect(page).toHaveURL(/\/login$/, { timeout: 20_000 });
  });

  test('a signed-in admin can navigate to another protected route', async ({ page }) => {
    await signIn(page, ADMIN);

    await page.getByRole('link', { name: 'Incidents' }).click();

    await expect(page).toHaveURL(/\/incidents$/);
    await expect(page.getByText(BACKEND_ERROR)).toHaveCount(0);
  });
});
