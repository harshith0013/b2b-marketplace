import { expect, test } from "@playwright/test";

test("root redirects to a language and shows the home page", async ({ page }) => {
  await page.goto("/");
  await expect(page).toHaveURL(/\/(en|te|hi)$/);
  await expect(page.getByRole("heading", { level: 1 })).toBeVisible();
});

test("switching to Telugu changes the page language", async ({ page }) => {
  await page.goto("/en");
  await page.getByRole("link", { name: "తెలుగు" }).click();
  await expect(page).toHaveURL(/\/te$/);
  await expect(page.locator("html")).toHaveAttribute("lang", "te");
});

test("health endpoint answers without leaking details", async ({ request }) => {
  const response = await request.get("/api/health");
  expect(response.ok()).toBeTruthy();
  const body = await response.json();
  expect(body.frontend).toBe("UP");
  expect(Object.keys(body).sort()).toEqual(["backend", "frontend"]);
});
