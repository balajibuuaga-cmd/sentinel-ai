import { test, expect, type Page } from '@playwright/test';

const ADMIN = { email: 'admin@sentinel.ai', password: 'sentinel-admin' };

async function signIn(page: Page) {
  await page.goto('/login');
  await page.getByLabel('Email').fill(ADMIN.email);
  await page.getByLabel('Password').fill(ADMIN.password);
  await Promise.all([
    page.waitForResponse(
      (response) => response.url().includes('/api/auth/login') && response.status() === 200,
      { timeout: 30_000 },
    ),
    page.getByRole('button', { name: 'Sign In' }).click(),
  ]);
  await expect(page.getByRole('link', { name: 'Command Center' })).toBeVisible({ timeout: 30_000 });
}

// Both of these nav entries previously had no route and rendered as disabled
// buttons — "Risks & Recommendations" even carried a live badge count that
// invited a click and then did nothing.
test.describe('risks & recommendations', () => {
  test('the nav entry is a working link, not a dead button', async ({ page }) => {
    await signIn(page);

    const link = page.getByRole('link', { name: /Risks & Rec/i });
    await expect(link).toHaveCount(1);
    await link.click();

    await expect(page).toHaveURL(/\/risks$/);
  });

  test('risks render with a severity breakdown', async ({ page }) => {
    await signIn(page);
    await page.goto('/risks');

    await expect(page.getByText('Risks & Recommendations').first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByRole('button', { name: /^All \d+$/ })).toBeVisible();
    await expect(page.locator('.risk-card').first()).toBeVisible();
    await expect(page.getByText('Recommended action').first()).toBeVisible();
  });

  test('the severity filter narrows the list', async ({ page }) => {
    await signIn(page);
    await page.goto('/risks');
    await expect(page.locator('.risk-card').first()).toBeVisible({ timeout: 20_000 });

    const total = await page.locator('.risk-card').count();
    await page.getByRole('button', { name: /^Critical \d+$/ }).click();

    const criticalOnly = await page.locator('.risk-card').count();
    expect(criticalOnly).toBeLessThanOrEqual(total);
    // Every remaining card must actually be critical.
    expect(await page.locator('.risk-card:not(.risk-card-critical)').count()).toBe(0);
  });

  // The sidebar badge and the page derive from one shared merge, so a change to
  // either must not let them drift apart.
  test('the sidebar badge count matches the page total', async ({ page }) => {
    await signIn(page);
    await page.goto('/risks');
    await expect(page.locator('.risk-card').first()).toBeVisible({ timeout: 20_000 });

    const badge = await page.locator('.nav-item .nav-badge').first().innerText();
    const allButton = await page.getByRole('button', { name: /^All \d+$/ }).innerText();

    expect(allButton.replace(/\D/g, '')).toBe(badge.trim());
  });
});

test.describe('ai copilot', () => {
  test('the nav entry is a working link, not a dead button', async ({ page }) => {
    await signIn(page);

    const link = page.getByRole('link', { name: 'AI Copilot' });
    await expect(link).toHaveCount(1);
    await link.click();

    await expect(page).toHaveURL(/\/copilot$/);
  });

  test('asking a question returns an answer from the reasoning provider', async ({ page }) => {
    await signIn(page);
    await page.goto('/copilot');

    await page.getByRole('textbox', { name: 'Ask Sentinel a question' })
      .fill('Summarize our current architecture risks.');
    await Promise.all([
      page.waitForResponse(
        (response) => response.url().includes('/api/ai/command') && response.status() === 200,
        { timeout: 30_000 },
      ),
      page.getByRole('button', { name: 'Send' }).click(),
    ]);

    const assistant = page.locator('.copilot-turn-assistant .copilot-bubble');
    await expect(assistant).toBeVisible({ timeout: 20_000 });
    // The placeholder must be replaced by a real answer, not left spinning.
    await expect(assistant).not.toHaveText('Thinking...');
    await expect(page.locator('.copilot-bubble-pending')).toHaveCount(0);
  });

  test('a suggestion chip asks its question', async ({ page }) => {
    await signIn(page);
    await page.goto('/copilot');

    await Promise.all([
      page.waitForResponse(
        (response) => response.url().includes('/api/ai/command') && response.status() === 200,
        { timeout: 30_000 },
      ),
      page.getByRole('button', { name: 'What should the team fix first this week?' }).click(),
    ]);

    await expect(page.locator('.copilot-turn-user .copilot-bubble')).toBeVisible();
    await expect(page.locator('.copilot-turn-assistant .copilot-bubble')).toBeVisible({ timeout: 20_000 });
  });
});
