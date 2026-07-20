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

// Every one of these controls rendered but did nothing: no onClick, no route.
// They are the kind of defect that only shows up when someone actually clicks
// around the product, so each gets a test that clicks it.
test.describe('chrome controls are wired', () => {
  test('the top bar search hands its question to the copilot', async ({ page }) => {
    await signIn(page);

    await page.getByLabel('Ask Sentinel anything').fill('What should the team fix first?');
    await Promise.all([
      page.waitForResponse(
        (response) => response.url().includes('/api/ai/command') && response.status() === 200,
        { timeout: 30_000 },
      ),
      page.getByLabel('Ask Sentinel anything').press('Enter'),
    ]);

    await expect(page).toHaveURL(/\/copilot/);
    await expect(page.locator('.copilot-turn-user .copilot-bubble')).toHaveText(
      'What should the team fix first?',
    );
    await expect(page.locator('.copilot-turn-assistant .copilot-bubble')).toBeVisible({
      timeout: 20_000,
    });
  });

  test('the handed-over question is not re-asked on reload', async ({ page }) => {
    await signIn(page);
    await page.getByLabel('Ask Sentinel anything').fill('Summarize our architecture risks.');
    await page.getByLabel('Ask Sentinel anything').press('Enter');
    await expect(page.locator('.copilot-turn-assistant .copilot-bubble')).toBeVisible({
      timeout: 30_000,
    });

    // The ?q= param is stripped after use, so a reload starts a clean thread.
    await expect(page).toHaveURL(/\/copilot$/);
    await page.reload();
    await expect(page.locator('.copilot-turn-user')).toHaveCount(0);
  });

  test('Cmd+K focuses the search field it advertises', async ({ page }) => {
    await signIn(page);

    await page.keyboard.press('ControlOrMeta+k');

    const focusedLabel = await page.evaluate(() =>
      document.activeElement?.getAttribute('aria-label'),
    );
    expect(focusedLabel).toBe('Ask Sentinel anything');
  });

  test('Create New Analysis opens the deployment simulator', async ({ page }) => {
    await signIn(page);

    await page.getByRole('button', { name: 'Create New Analysis' }).click();

    await expect(page).toHaveURL(/\/simulator$/);
  });

  test('the top bar shield and bolt actions navigate', async ({ page }) => {
    await signIn(page);

    await page.getByTitle('Secret Shield').click();
    await expect(page).toHaveURL(/\/secret-shield$/);

    await page.getByTitle('Run a deployment analysis').click();
    await expect(page).toHaveURL(/\/simulator$/);
  });

  test('View Impact Analysis opens executive mode', async ({ page }) => {
    await signIn(page);

    await page.getByRole('link', { name: /View Impact Analysis/i }).click();

    await expect(page).toHaveURL(/\/executive$/);
  });

  test('the topology expand control opens the architecture view', async ({ page }) => {
    await signIn(page);

    await page.getByTitle('Open full architecture view').click();

    await expect(page).toHaveURL(/\/architecture$/);
  });

  test('the dashboard copilot panel expands to the full page', async ({ page }) => {
    await signIn(page);

    await page.getByTitle('Open full AI Copilot').click();

    await expect(page).toHaveURL(/\/copilot$/);
  });

  // How much audit history exists depends on what ran before this spec, so assert
  // the invariant rather than one branch: the control appears only when there is
  // genuinely more to show, and when it appears it actually expands. Skipping on
  // the short branch would leave this untested most of the time.
  test('the activity feed expands in place, and offers no control when it cannot', async ({ page }) => {
    await signIn(page);
    await expect(page.locator('.activity-list li').first()).toBeVisible({ timeout: 20_000 });

    const collapsed = await page.locator('.activity-list li').count();
    const viewAll = page.getByRole('button', { name: /View All Activity/i });

    if (collapsed < 8) {
      // Nothing beyond the collapsed view: the button must not be offered at all.
      await expect(viewAll).toHaveCount(0);
      return;
    }

    await expect(viewAll).toHaveCount(1);
    await viewAll.click();

    await expect(page.getByRole('button', { name: /Show Less/i })).toBeVisible();
    expect(await page.locator('.activity-list li').count()).toBeGreaterThan(collapsed);
  });

  test('no nav item is rendered as a dead disabled button', async ({ page }) => {
    await signIn(page);

    await expect(page.locator('.nav-item-disabled')).toHaveCount(0);
  });
});
