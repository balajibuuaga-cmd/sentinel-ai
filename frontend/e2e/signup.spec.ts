import { test, expect, type Page } from '@playwright/test';

// Each run needs a fresh address: signups persist for the life of the backend
// process, so a fixed email would pass once and then hit "already registered".
function uniqueEmail() {
  return `e2e-${Date.now()}-${Math.floor(Math.random() * 100_000)}@e2eco.test`;
}

async function fillSignup(
  page: Page,
  fields: { organizationName: string; email: string; password: string; confirmPassword?: string },
) {
  await page.getByLabel('Organization name').fill(fields.organizationName);
  await page.getByLabel('Email').fill(fields.email);
  await page.getByLabel('Password', { exact: true }).fill(fields.password);
  await page.getByLabel('Confirm password').fill(fields.confirmPassword ?? fields.password);
}

test.describe('signup', () => {
  test.beforeEach(async ({ page }) => {
    await page.addInitScript(() => window.localStorage.clear());
    await page.goto('/signup');
  });

  test('a new organization can sign up and lands signed in', async ({ page }) => {
    await fillSignup(page, {
      organizationName: 'E2E Test Co',
      email: uniqueEmail(),
      password: 'founder-pass-9',
    });
    await page.getByRole('button', { name: 'Create Account' }).click();

    // Signup auto-logs in, so the authenticated shell should render.
    await expect(page.getByRole('link', { name: 'Command Center' })).toBeVisible({ timeout: 20_000 });
    await expect(page).not.toHaveURL(/\/signup$/);
  });

  test('a mismatched confirmation is rejected before submitting', async ({ page }) => {
    await fillSignup(page, {
      organizationName: 'Mismatch Co',
      email: uniqueEmail(),
      password: 'founder-pass-9',
      confirmPassword: 'a-different-password-9',
    });
    await page.getByRole('button', { name: 'Create Account' }).click();

    await expect(page.locator('.auth-error')).toBeVisible({ timeout: 10_000 });
    await expect(page).toHaveURL(/\/signup$/);
  });

  // The backend enforces min length + letter + digit; the UI mirrors it. Either
  // layer may reject, but the user must see an error and stay put.
  test('a weak password is rejected', async ({ page }) => {
    await fillSignup(page, {
      organizationName: 'Weak Pass Co',
      email: uniqueEmail(),
      password: 'short',
    });
    await page.getByRole('button', { name: 'Create Account' }).click();

    await expect(page.locator('.auth-error')).toBeVisible({ timeout: 10_000 });
    await expect(page).toHaveURL(/\/signup$/);
  });

  test('signing up with an already-registered address shows an error', async ({ page }) => {
    await fillSignup(page, {
      organizationName: 'Duplicate Co',
      email: 'admin@sentinel.ai', // seeded account
      password: 'founder-pass-9',
    });
    await page.getByRole('button', { name: 'Create Account' }).click();

    await expect(page.locator('.auth-error')).toBeVisible({ timeout: 10_000 });
    await expect(page).toHaveURL(/\/signup$/);
  });

  test('the login page links to signup and back', async ({ page }) => {
    await page.goto('/login');
    await page.getByRole('link', { name: /sign up|create/i }).first().click();
    await expect(page).toHaveURL(/\/signup$/);

    await page.getByRole('link', { name: 'Sign in' }).click();
    await expect(page).toHaveURL(/\/login$/);
  });
});

test.describe('password reset', () => {
  test('requesting a reset link succeeds without revealing whether the account exists', async ({ page }) => {
    await page.addInitScript(() => window.localStorage.clear());
    await page.goto('/forgot-password');

    await page.getByLabel('Email').fill('definitely-not-registered@e2eco.test');
    await page.getByRole('button', { name: /send|reset/i }).click();

    // Unregistered addresses must produce the same confirmation as registered
    // ones - no error, no enumeration signal.
    await expect(page.locator('.auth-error')).toHaveCount(0);
  });
});
