import { expect, test } from "@playwright/test";

test("root redirects to a language and shows the home page", async ({ page }) => {
  await page.goto("/");
  await expect(page).toHaveURL(/\/(en|te|hi)$/);
  await expect(page.getByRole("heading", { level: 1 })).toBeVisible();
});

test("switching to Telugu changes the page language and keeps the page", async ({ page }) => {
  await page.goto("/en/sell");
  await page.getByRole("link", { name: "తెలుగు" }).click();
  await expect(page).toHaveURL(/\/te\/sell$/);
  await expect(page.locator("html")).toHaveAttribute("lang", "te");
});

test("status page shows the website as working", async ({ page }) => {
  await page.goto("/en/status");
  await expect(page.getByRole("heading", { level: 1, name: "System status" })).toBeVisible();
  // The backend is not running during these tests, so only the website row is asserted.
  await expect(page.getByRole("definition").first()).toHaveText("Working");
});

test("health endpoint answers without leaking details", async ({ request }) => {
  const response = await request.get("/api/health");
  expect(response.ok()).toBeTruthy();
  const body = await response.json();
  expect(body.frontend).toBe("UP");
  expect(Object.keys(body).sort()).toEqual(["backend", "frontend"]);
});
