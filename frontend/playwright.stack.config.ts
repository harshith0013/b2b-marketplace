import { defineConfig, devices } from "@playwright/test";

/**
 * End-to-end tests against the whole system started by `docker compose up --build`: website,
 * backend, PostgreSQL and the mock sign-in server. Nothing is mocked inside the browser.
 */
export default defineConfig({
  testDir: "./e2e-stack",
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? "github" : "list",
  use: {
    baseURL: process.env.STACK_BASE_URL ?? "http://localhost:3000",
    trace: "on-first-retry",
  },
  projects: [
    { name: "mobile", use: { ...devices["Pixel 7"] } },
    { name: "desktop", use: { ...devices["Desktop Chrome"] } },
  ],
});
