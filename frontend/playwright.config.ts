import { defineConfig, devices } from '@playwright/test';

// End-to-end tests drive the real app in a browser: they boot the Spring Boot
// backend (H2 in-memory, seeded demo accounts) and the Vite dev server, then
// exercise the actual login/routing flow. The frontend proxies /api to the
// backend on 8090, exactly like local dev.
export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  workers: 1,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [['github'], ['line']] : 'line',
  use: {
    // Isolated port so E2E never collides with other dev servers on 5173.
    baseURL: 'http://localhost:5199',
    trace: 'on-first-retry',
  },
  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
  ],
  // reuseExistingServer:false → always boot Sentinel's own servers, so a foreign
  // app already on a port can never be mistaken for ours.
  webServer: [
    {
      // Requires the jar to be built first: ./mvnw -f backend/pom.xml -DskipTests package
      command: 'java -jar ../backend/target/sentinel-ai-0.0.1-SNAPSHOT.jar',
      url: 'http://localhost:8090/api/auth/status',
      reuseExistingServer: false,
      timeout: 120_000,
    },
    {
      command: 'npm run dev -- --port 5199 --strictPort',
      url: 'http://localhost:5199',
      reuseExistingServer: false,
      timeout: 60_000,
    },
  ],
});
