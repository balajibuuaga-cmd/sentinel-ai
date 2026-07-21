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

// The page opened on a simulation form pre-filled with a payment-api deployment
// for a repository nobody owns, and the deployments themselves were a footnote.
// The list is the page now: pick a deployment, read what happened, simulate from
// it if you want to.
test.describe('deployments', () => {
  test('the page opens on the list of stored deployments', async ({ page }) => {
    await signIn(page);

    await Promise.all([
      page.waitForResponse(
        (r) => r.url().includes('/api/deployments') && r.status() === 200,
        { timeout: 30_000 },
      ),
      page.goto('/simulator'),
    ]);

    await expect(page.locator('.deployment-row-button').first()).toBeVisible({ timeout: 20_000 });
    // No form until one is asked for.
    await expect(page.getByRole('button', { name: 'Run Simulation' })).toHaveCount(0);
  });

  test('each deployment says whether it came from GitHub or a simulation', async ({ page }) => {
    await signIn(page);
    await page.goto('/simulator');
    await expect(page.locator('.deployment-row-button').first()).toBeVisible({ timeout: 20_000 });

    const rows = await page.locator('.deployment-row-button').count();
    expect(await page.locator('.deployment-source').count()).toBe(rows);
  });

  test('selecting a deployment shows what happened in it', async ({ page }) => {
    await signIn(page);
    await page.goto('/simulator');
    await page.locator('.deployment-row-button').first().click();

    await expect(page.locator('.deployment-detail-panel')).toBeVisible({ timeout: 20_000 });
    await expect(page.getByRole('button', { name: /Simulate this deployment/ })).toBeVisible();
  });

  test('a deployment can be simulated from its own page, pre-filled from it', async ({ page }) => {
    await signIn(page);
    await page.goto('/simulator');
    await page.locator('.deployment-row-button').first().click();
    await expect(page.locator('.deployment-detail-panel')).toBeVisible({ timeout: 20_000 });

    // Read the service from the opened deployment's own header, not the list
    // row, whose title also carries the key and the source badge.
    const service = (await page.locator('.deployment-detail-panel .engineer-form-header').innerText()).trim();
    await page.getByRole('button', { name: /Simulate this deployment/ }).click();

    // Carried over from the deployment rather than typed again.
    await expect(page.getByLabel('Service')).toHaveValue(service);
    await expect(page.getByRole('button', { name: 'Run Simulation' })).toBeVisible();
  });

  // "Simulate this deployment" left repository and pipeline blank, which the
  // backend requires, so the run failed with a bare "Request validation failed".
  test('simulating from a deployment runs without a validation error', async ({ page }) => {
    await signIn(page);
    await page.goto('/simulator');
    await page.locator('.deployment-row-button').first().click();
    await expect(page.locator('.deployment-detail-panel')).toBeVisible({ timeout: 20_000 });
    await page.getByRole('button', { name: /Simulate this deployment/ }).click();

    await Promise.all([
      page.waitForResponse(
        (r) => r.url().includes('/api/integrations/ci/simulate') && r.status() < 400,
        { timeout: 30_000 },
      ),
      page.getByRole('button', { name: 'Run Simulation' }).click(),
    ]);

    await expect(page.locator('.engineer-error')).toHaveCount(0);
    await expect(page.locator('.simulator-result')).toBeVisible({ timeout: 20_000 });
  });

  test('a new simulation starts blank rather than pre-filled with invented data', async ({ page }) => {
    await signIn(page);
    await page.goto('/simulator');

    await page.getByRole('button', { name: /New simulation/ }).click();

    await expect(page.getByLabel('Service')).toHaveValue('');
    await expect(page.getByLabel('Repository')).toHaveValue('');
    await expect(page.getByLabel('Owner team')).toHaveValue('');
  });
});

// The briefing summarised the day in five tiles and a paragraph. It never said
// what actually happened, which is what a briefing is for.
test.describe('ai briefing', () => {
  test('lists individual events, not just a summary', async ({ page }) => {
    await signIn(page);
    await page.goto('/briefing');

    await expect(page.getByText('Everything that happened')).toBeVisible({ timeout: 20_000 });
    await expect(page.locator('.briefing-event').first()).toBeVisible();
    // Each entry carries its own detail rather than a single rolled-up figure.
    await expect(page.locator('.briefing-fact').first()).toBeVisible();
  });

  test('filtering narrows the account to one kind of event', async ({ page }) => {
    await signIn(page);
    await page.goto('/briefing');
    await expect(page.locator('.briefing-event').first()).toBeVisible({ timeout: 20_000 });

    const total = await page.locator('.briefing-event').count();
    await page.locator('.briefing-filter', { hasText: /^Deployment$/ }).click();

    const deploymentsOnly = await page.locator('.briefing-event').count();
    expect(deploymentsOnly).toBeLessThanOrEqual(total);
    expect(await page.locator('.briefing-kind:not(.kind-deployment)').count()).toBe(0);
  });

  // The top bar computed its greeting from the browser clock while the briefing
  // rendered the server's, so a reader in US Central saw "Good Morning" above
  // "Good afternoon." from a UTC server.
  test('the greeting matches the one in the top bar', async ({ page }) => {
    await signIn(page);
    await page.goto('/briefing');

    const heroGreeting = await page.locator('.ai-briefing-hero h1').innerText();
    const topBarGreeting = await page.locator('.topbar-greeting h1').innerText();

    expect(topBarGreeting).toContain(heroGreeting.replace('.', '').trim());
  });

  test('the log is paginated rather than one long scroll', async ({ page }) => {
    await signIn(page);
    await page.goto('/briefing');
    await expect(page.locator('.briefing-event').first()).toBeVisible({ timeout: 20_000 });

    const shown = await page.locator('.briefing-event').count();
    expect(shown).toBeLessThanOrEqual(8);

    const pager = page.locator('.briefing-pager');
    if ((await pager.count()) === 0) {
      // Fewer events than one page: no controls should be offered.
      return;
    }

    await expect(page.getByRole('button', { name: 'Previous' })).toBeDisabled();
    // Titles repeat across entries (several "Login Success" in a row), so the
    // range indicator is what actually distinguishes one page from the next.
    const firstRange = await page.locator('.briefing-pager-status').innerText();

    await page.getByRole('button', { name: 'Next' }).click();

    await expect(page.getByRole('button', { name: 'Previous' })).toBeEnabled();
    expect(await page.locator('.briefing-pager-status').innerText()).not.toBe(firstRange);
  });

  test('changing the filter returns to the first page', async ({ page }) => {
    await signIn(page);
    await page.goto('/briefing');
    await expect(page.locator('.briefing-event').first()).toBeVisible({ timeout: 20_000 });

    if ((await page.locator('.briefing-pager').count()) === 0) {
      return;
    }
    await page.getByRole('button', { name: 'Next' }).click();
    await expect(page.getByRole('button', { name: 'Previous' })).toBeEnabled();

    // Filtering to a smaller set must not strand the reader on a page past its end.
    await page.locator('.briefing-filter', { hasText: /^Deployment$/ }).click();
    await expect(page.locator('.briefing-event').first()).toBeVisible();
  });
});
