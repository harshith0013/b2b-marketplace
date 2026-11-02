import AxeBuilder from "@axe-core/playwright";
import { expect, test } from "@playwright/test";
import en from "../src/i18n/dictionaries/en.json";
import hi from "../src/i18n/dictionaries/hi.json";
import te from "../src/i18n/dictionaries/te.json";

const dictionaries = { en, te, hi };
const WCAG_TAGS = ["wcag2a", "wcag2aa", "wcag21a", "wcag21aa", "wcag22aa"];

const shells = [
  { path: "", heading: (d: typeof en) => d.home.tagline },
  { path: "/sell", heading: (d: typeof en) => d.sell.title },
  { path: "/sell/new", heading: (d: typeof en) => d.wizard.title },
  { path: "/status", heading: (d: typeof en) => d.status.heading },
];

for (const [lang, dict] of Object.entries(dictionaries)) {
  for (const shell of shells) {
    const url = `/${lang}${shell.path}`;

    test(`${url} renders in its language without accessibility violations`, async ({ page }) => {
      await page.goto(url);

      await expect(page.locator("html")).toHaveAttribute("lang", lang);
      await expect(page.getByRole("heading", { level: 1, name: shell.heading(dict) })).toBeVisible();
      await expect(page.getByText(dict.footer.sampleNotice)).toBeVisible();

      const results = await new AxeBuilder({ page }).withTags(WCAG_TAGS).analyze();
      expect(results.violations.map((v) => `${v.id}: ${v.help}`)).toEqual([]);

      // No sideways scrolling on any screen size.
      const overflow = await page.evaluate(
        () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
      );
      expect(overflow).toBeLessThanOrEqual(0);
    });
  }
}

test("buyer home: search explains it is coming soon", async ({ page }) => {
  await page.goto("/en");
  await page.getByLabel(en.home.searchLabel).fill("soap");
  await page.getByLabel(en.home.searchLabel).press("Enter");
  await expect(page.getByRole("status")).toContainText(en.home.searchComingSoon);
});

test("buyer home: assistant dialog opens and closes with Escape", async ({ page }) => {
  await page.goto("/en");
  await page.getByRole("button", { name: en.home.assistant.open }).click();
  const dialog = page.getByRole("dialog", { name: en.home.assistant.title });
  await expect(dialog).toBeVisible();
  await page.keyboard.press("Escape");
  await expect(dialog).toBeHidden();
});

test("buyer home: every sample offer is labelled as sample data", async ({ page }) => {
  await page.goto("/en");
  const offers = page.getByRole("region", { name: en.home.offersHeading }).getByRole("listitem");
  await expect(offers).toHaveCount(Object.keys(en.home.offers).length);
  for (const offer of await offers.all()) {
    await expect(offer.getByText(en.common.sampleData)).toBeVisible();
  }
});

test("seller can go from home to the listing wizard and reach the review step", async ({ page }) => {
  await page.goto("/en");
  await page
    .getByRole("main")
    .getByRole("link", { name: new RegExp(en.home.tiles.sell.title) })
    .click();
  await expect(page).toHaveURL(/\/en\/sell$/);
  await page.getByRole("link", { name: en.sell.newListing }).click();
  await expect(page).toHaveURL(/\/en\/sell\/new$/);

  await page.getByLabel(en.wizard.fields.productName).fill("Bath soap");
  for (let step = 1; step < 5; step++) {
    await page.getByRole("button", { name: en.common.next }).click();
  }

  await expect(page.getByRole("heading", { level: 2, name: en.wizard.steps.review })).toBeFocused();
  await expect(page.getByRole("definition").getByText("Bath soap", { exact: true })).toBeVisible();
  await expect(page.getByRole("button", { name: en.wizard.submit })).toBeDisabled();

  const results = await new AxeBuilder({ page }).withTags(WCAG_TAGS).analyze();
  expect(results.violations.map((v) => `${v.id}: ${v.help}`)).toEqual([]);
});
