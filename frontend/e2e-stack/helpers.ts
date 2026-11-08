import AxeBuilder from "@axe-core/playwright";
import { expect, type Browser, type Page } from "@playwright/test";
import en from "../src/i18n/dictionaries/en.json";

const WCAG_TAGS = ["wcag2a", "wcag2aa", "wcag21a", "wcag21aa", "wcag22aa"];

export function uniqueEmail(name: string): string {
  return `${name}-${crypto.randomUUID()}@example.com`;
}

/** Opens a protected page while signed out and signs in through the mock "Google" page. */
export async function signIn(
  page: Page,
  email: string,
  path = "/en/account",
  dict = en,
) {
  await page.goto(path);
  await expect(page).toHaveURL(/\/login\?returnTo=/);
  await page.getByRole("link", { name: dict.auth.continueWithGoogle }).click();
  await page.locator("input[name=username]").fill(email);
  await page.locator("input[type=submit]").click();
  await expect(page).toHaveURL(new RegExp(`${path.replaceAll("/", "\\/")}$`));
}

export async function newSignedInPage(
  browser: Browser,
  email: string,
  path?: string,
): Promise<Page> {
  const page = await (await browser.newContext()).newPage();
  await signIn(page, email, path);
  return page;
}

export async function expectNoA11yViolations(page: Page) {
  const results = await new AxeBuilder({ page }).withTags(WCAG_TAGS).analyze();
  expect(results.violations.map((v) => `${v.id}: ${v.help}`)).toEqual([]);
}

/** Creates a business through the onboarding form; returns its page URL. */
export async function createBusiness(
  page: Page,
  name: string,
): Promise<string> {
  await page.goto("/en/onboarding");
  await page.getByLabel(en.business.fields.legalName).fill(name);
  await page.getByLabel(en.business.fields.businessType).selectOption("KIRANA");
  await page.getByLabel(en.business.fields.city).fill("Guntur");
  await page.getByLabel(en.business.fields.state).fill("Andhra Pradesh");
  await page.getByLabel(en.business.fields.pincode).fill("522001");
  await page.getByRole("button", { name: en.business.create }).click();
  await expect(page).toHaveURL(/\/en\/business\/[0-9a-f-]{36}$/);
  await expect(page.getByRole("heading", { level: 1, name })).toBeVisible();
  return new URL(page.url()).pathname;
}

/** Fills the add-warehouse form. Scoped to its section: Next.js keeps earlier pages hidden in the DOM. */
export async function addWarehouse(page: Page, name: string) {
  const form = page.getByRole("region", { name: en.warehouses.addHeading });
  const fields = en.warehouses.fields;
  await form.getByLabel(fields.name, { exact: true }).fill(name);
  await form
    .getByLabel(fields.addressLine, { exact: true })
    .fill("12-3 Main Road");
  await form.getByLabel(fields.city, { exact: true }).fill("Guntur");
  await form.getByLabel(fields.state, { exact: true }).fill("Andhra Pradesh");
  await form.getByLabel(fields.pincode, { exact: true }).fill("522002");
  await form.getByRole("button", { name: en.warehouses.add }).click();
}
