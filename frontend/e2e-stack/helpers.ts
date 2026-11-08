import AxeBuilder from "@axe-core/playwright";
import path from "node:path";
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
export async function addWarehouse(page: Page, name: string, city = "Guntur") {
  const form = page.getByRole("region", { name: en.warehouses.addHeading });
  const fields = en.warehouses.fields;
  await form.getByLabel(fields.name, { exact: true }).fill(name);
  await form
    .getByLabel(fields.addressLine, { exact: true })
    .fill("12-3 Main Road");
  await form.getByLabel(fields.city, { exact: true }).fill(city);
  await form.getByLabel(fields.state, { exact: true }).fill("Andhra Pradesh");
  await form.getByLabel(fields.pincode, { exact: true }).fill("522002");
  await form.getByRole("button", { name: en.warehouses.add }).click();
}

/** Made an admin by BOOTSTRAP_ADMIN_EMAIL in docker-compose.yml (a reserved .test address). */
export const REVIEWER = "e2e-reviewer@example.test";

const FIXTURES = path.join(__dirname, "fixtures");

export function isoDateInDays(days: number): string {
  const date = new Date();
  date.setUTCDate(date.getUTCDate() + days);
  return date.toISOString().slice(0, 10);
}

export async function uploadListingPhoto(page: Page, file: string) {
  const form = page.getByRole("region", { name: en.listings.photosHeading });
  await form
    .getByLabel(en.listings.photoFile, { exact: true })
    .setInputFiles(path.join(FIXTURES, file));
  await form.getByRole("button", { name: en.listings.upload }).click();
  await expect(
    form.getByRole("status").filter({ hasText: en.listings.uploaded }),
  ).toBeVisible();
}

/**
 * Puts an offer on sale through the real screens: a seller lists it with photos and submits, the
 * test reviewer approves. Returns the seller's business name and the listing page URL.
 */
export async function publishOffer(
  browser: Browser,
  offer: {
    productName: string;
    city: string;
    confidential: boolean;
    businessName: string;
    /** Keep the seller signed in and return the page, for tests that go on as the seller. */
    keepSeller?: boolean;
  },
): Promise<{ listingUrl: string; listingId: string; seller: Page | null }> {
  const seller = await newSignedInPage(browser, uniqueEmail("seller"));
  const businessPath = await createBusiness(seller, offer.businessName);
  await seller.goto(`${businessPath}/warehouses`);
  await addWarehouse(seller, "Main godown", offer.city);
  await expect(
    seller.getByRole("status").filter({ hasText: en.warehouses.added }),
  ).toBeVisible();

  await seller.goto(`${businessPath}/listings/new`);
  const form = seller.getByRole("form", { name: en.wizard.title });
  const next = () =>
    seller.getByRole("button", { name: new RegExp(en.common.next) }).click();
  await form.getByRole("radio", { name: en.wizard.product.new }).check();
  await form
    .getByLabel(en.wizard.product.category, { exact: true })
    .selectOption({ label: "Home care › Laundry detergents" });
  await form
    .getByLabel(en.wizard.product.brand, { exact: true })
    .fill("Sunbright");
  await form
    .getByLabel(en.wizard.product.name, { exact: true })
    .fill(offer.productName);
  await form
    .getByLabel(en.wizard.product.packSize, { exact: true })
    .fill("500");
  await next();
  await form
    .getByLabel(en.wizard.batch.warehouse, { exact: true })
    .selectOption({ index: 1 });
  await form
    .getByLabel(en.wizard.batch.batchNumber, { exact: true })
    .fill("D-1107");
  await form
    .getByLabel(en.wizard.batch.expiresOn, { exact: true })
    .fill(isoDateInDays(75));
  await form.getByLabel(en.wizard.batch.mrp, { exact: true }).fill("60");
  await form.getByLabel(en.wizard.batch.quantity, { exact: true }).fill("400");
  await next();
  await form.getByLabel(en.wizard.price.moq, { exact: true }).fill("20");
  await form.getByLabel(en.wizard.price.tierPrice, { exact: true }).fill("42");
  if (offer.confidential) {
    await form
      .getByLabel(en.wizard.price.visibility, { exact: true })
      .selectOption("CONFIDENTIAL");
  }
  await next();
  await seller.getByRole("button", { name: en.wizard.save }).click();
  await expect(seller).toHaveURL(/\/listings\/[0-9a-f-]{36}$/);
  const listingUrl = seller.url();
  await uploadListingPhoto(seller, "pack-front.png");
  await uploadListingPhoto(seller, "label-date.png");
  await waitForLabelReading(seller);
  await seller.getByRole("button", { name: en.listings.submit }).click();
  await expect(seller.getByText(en.listings.inReviewNote)).toBeVisible();

  const reviewer = await newSignedInPage(
    browser,
    REVIEWER,
    "/en/admin/reviews",
  );
  await reviewer
    .getByRole("link", { name: new RegExp(offer.productName) })
    .click();
  await approveAsReviewer(reviewer);
  await expect(reviewer).toHaveURL(/\/en\/admin\/reviews$/);
  await reviewer.close();
  const listingId = listingUrl.split("/").at(-1) ?? "";
  if (!offer.keepSeller) {
    await seller.close();
    return { listingUrl, listingId, seller: null };
  }
  return { listingUrl, listingId, seller };
}

/**
 * Label photos are read in the background after upload: reload until the reading is shown, so the
 * reviewer later sees the same flags the rules use.
 */
export async function waitForLabelReading(page: Page) {
  for (let attempt = 0; attempt < 30; attempt++) {
    if ((await page.getByText(en.listings.labelPending).count()) === 0) return;
    await page.waitForTimeout(500);
    await page.reload();
  }
  throw new Error("The label photo was not read in time");
}

/** Approves in the review page, ticking the reviewer's own confirmation when flags ask for it. */
export async function approveAsReviewer(reviewer: Page) {
  // The review page streams in: wait for the decision form before looking for the confirmation.
  const approve = reviewer.getByRole("button", { name: en.review.approve });
  await expect(approve).toBeVisible();
  const confirm = reviewer.getByRole("checkbox", {
    name: en.review.confirmLabel,
  });
  // A re-render while the page settles can clear the tick: check it stuck, retry if refused.
  await expect(async () => {
    if ((await confirm.count()) > 0) {
      await confirm.check();
      await expect(confirm).toBeChecked();
    }
    await approve.click();
    await expect(reviewer).toHaveURL(/\/en\/admin\/reviews$/, {
      timeout: 5_000,
    });
  }).toPass({ timeout: 30_000 });
}
