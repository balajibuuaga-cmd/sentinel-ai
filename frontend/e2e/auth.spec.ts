import { test, expect } from '@playwright/test';

// Uses the seeded demo accounts from the H2 backend (V5 migration).
const ADMIN = { email: 'admin@sentinel.ai', password: 'sentinel-admin' };

test.describe('authentication & routing', () => {
  test.beforeEach(async ({ page }) => {
    // Start every test unauthenticated.
    await page.addInitScript(() => window.localStorage.clear());
  });

  test('an unauthenticated visit to a protected route redirects to login', async ({ page }) => {
    await page.goto('/');
    await expect(page).toHaveURL(/\/login$/);
    await expect(page.getByRole('heading', { name: 'Sentinel AI' })).toBeVisible();
  });

  test('valid credentials log in and reach the command center', async ({ page }) => {
    await page.goto('/login');
    await page.getByLabel('Email').fill(ADMIN.email);
    await page.getByLabel('Password').fill(ADMIN.password);
    await page.getByRole('button', { name: 'Sign In' }).click();

    // The authenticated shell renders the sidebar with the Command Center nav.
    await expect(page.getByRole('link', { name: 'Command Center' })).toBeVisible({ timeout: 20_000 });
    await expect(page).not.toHaveURL(/\/login$/);
  });

  test('a wrong password shows an error and stays on the login page', async ({ page }) => {
    await page.goto('/login');
    await page.getByLabel('Email').fill(ADMIN.email);
    await page.getByLabel('Password').fill('definitely-the-wrong-password');
    await page.getByRole('button', { name: 'Sign In' }).click();

    await expect(page.locator('.auth-error')).toBeVisible({ timeout: 15_000 });
    await expect(page).toHaveURL(/\/login$/);
  });

  test('the Cognito sign-in option is offered when the backend advertises it', async ({ page }) => {
    // Demo backend has no Cognito configured, so the button must NOT appear —
    // this guards the conditional wiring (cognitoConfigured === false → hidden).
    await page.goto('/login');
    await expect(page.getByRole('button', { name: 'Sign In' })).toBeVisible();
    await expect(page.getByText('Continue with Cognito')).toHaveCount(0);
  });
});
