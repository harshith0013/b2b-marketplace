import AxeBuilder from "@axe-core/playwright";
import { expect, test, type Browser, type Page } from "@playwright/test";
import en from "../src/i18n/dictionaries/en.json";
import te from "../src/i18n/dictionaries/te.json";

const WCAG_TAGS = ["wcag2a", "wcag2aa", "wcag21a", "wcag21aa", "wcag22aa"];

function uniqueEmail(name: string): string {
  return `${name}-${crypto.randomUUID()}@example.com`;
}

/** Opens a protected page while signed out and signs in through the mock "Google" page. */
async function signIn(page: Page, email: string, path = "/en/account", dict = en) {
  await page.goto(path);
  await expect(page).toHaveURL(/\/login\?returnTo=/);
  await page.getByRole("link", { name: dict.auth.continueWithGoogle }).click();
  await page.locator("input[name=username]").fill(email);
  await page.locator("input[type=submit]").click();
  await expect(page).toHaveURL(new RegExp(`${path.replaceAll("/", "\\/")}$`));
}

async function newSignedInPage(browser: Browser, email: string, path?: string): Promise<Page> {
  const page = await (await browser.newContext()).newPage();
  await signIn(page, email, path);
  return page;
}

async function expectNoA11yViolations(page: Page) {
  const results = await new AxeBuilder({ page }).withTags(WCAG_TAGS).analyze();
  expect(results.violations.map((v) => `${v.id}: ${v.help}`)).toEqual([]);
}

/** Creates a business through the onboarding form; returns its page URL. */
async function createBusiness(page: Page, name: string): Promise<string> {
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

test("a new person signs in, creates a business and finds it on their account", async ({ page }) => {
  const email = uniqueEmail("asha");
  await signIn(page, email);

  await expect(page.getByText(email)).toBeVisible();
  await expect(page.getByText(en.account.noBusinesses)).toBeVisible();
  await expectNoA11yViolations(page);

  await page.getByRole("link", { name: en.account.createBusiness }).click();
  await expect(page).toHaveURL(/\/en\/onboarding$/);
  await expectNoA11yViolations(page);

  const businessPath = await createBusiness(page, "Asha Kirana Stores");
  await expect(page.getByText(`${en.business.yourRole}: ${en.account.roles.OWNER}`)).toBeVisible();
  await expectNoA11yViolations(page);

  await page.goto("/en/account");
  await expect(page.getByRole("link", { name: /Asha Kirana Stores/ })).toHaveAttribute("href", businessPath);
});

test("an invited person accepts and joins with a limited role", async ({ browser }) => {
  const owner = await newSignedInPage(browser, uniqueEmail("owner"));
  const businessPath = await createBusiness(owner, "Ravi Wholesale");
  const staffEmail = uniqueEmail("staff");

  await owner.getByLabel(en.business.inviteEmail).fill(staffEmail);
  await owner.getByRole("button", { name: en.business.invite }).click();
  await expect(owner.getByRole("status").filter({ hasText: en.business.invited })).toBeVisible();
  await expect(owner.getByText(staffEmail)).toBeVisible();

  const staff = await newSignedInPage(browser, staffEmail);
  await expect(staff.getByText(/Ravi Wholesale/)).toBeVisible();
  await staff.getByRole("button", { name: en.account.accept }).click();
  await expect(staff.getByRole("link", { name: /Ravi Wholesale/ })).toBeVisible();

  await staff.goto(businessPath);
  await expect(staff.getByText(`${en.business.yourRole}: ${en.account.roles.STAFF}`)).toBeVisible();
  // Staff see the details but cannot edit them or manage the team.
  await expect(staff.getByRole("button", { name: en.business.save })).toHaveCount(0);
  await expect(staff.getByRole("heading", { name: en.business.inviteHeading })).toHaveCount(0);

  await owner.reload();
  await expect(owner.getByText(staffEmail)).toBeVisible(); // now listed as a member
  await expect(owner.getByText(en.business.noInvitations)).toBeVisible();
});

test("another business cannot open this business's page", async ({ browser }) => {
  const owner = await newSignedInPage(browser, uniqueEmail("owner"));
  const businessPath = await createBusiness(owner, "Private Traders");

  const stranger = await newSignedInPage(browser, uniqueEmail("stranger"));
  await stranger.goto(businessPath);
  // The page streams, so the HTTP status is already sent; Next.js swaps in its not-found page.
  await expect(stranger.getByText(/could not be found/i)).toBeVisible();
  await expect(stranger.getByText("Private Traders")).toHaveCount(0);
  await expect(stranger.getByRole("heading", { name: en.business.detailsHeading })).toHaveCount(0);
});

test("signing out ends the session", async ({ page }) => {
  await signIn(page, uniqueEmail("leaving"));
  await page.getByRole("button", { name: en.auth.signOut }).click();
  await expect(page).toHaveURL(/\/en$/);
  await expect(page.getByRole("link", { name: en.auth.signIn })).toBeVisible();

  await page.goto("/en/account");
  await expect(page).toHaveURL(/\/en\/login\?returnTo=/);
});

test("signed-in sellers reach the listing wizard", async ({ page }) => {
  await signIn(page, uniqueEmail("seller"), "/en/sell");
  await page.getByRole("link", { name: en.sell.newListing }).click();
  await expect(page).toHaveURL(/\/en\/sell\/new$/);
  for (let step = 1; step < 5; step++) {
    await page.getByRole("button", { name: en.common.next }).click();
  }
  await expect(page.getByRole("button", { name: en.wizard.submit })).toBeDisabled();
  await expectNoA11yViolations(page);
});

test("sign-in and the account page work in Telugu", async ({ page }) => {
  await signIn(page, uniqueEmail("telugu"), "/te/account", te);
  await expect(page.locator("html")).toHaveAttribute("lang", "te");
  await expect(page.getByRole("heading", { level: 1, name: te.account.title })).toBeVisible();
  await expectNoA11yViolations(page);
});
