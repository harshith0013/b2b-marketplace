import { expect, test } from "@playwright/test";
import en from "../src/i18n/dictionaries/en.json";
import te from "../src/i18n/dictionaries/te.json";
import { expectNoA11yViolations, publishOffer } from "./helpers";

test("an anonymous buyer finds a confidential offer with a misspelled search and opens it", async ({
  browser,
}) => {
  test.setTimeout(120_000);
  const id = crypto.randomUUID().slice(0, 6);
  // A city of its own, so this test's offer is easy to pick out among earlier runs' offers.
  const city = `Testpur ${id}`;
  const productName = `Detergent Powder ${id}`;
  const businessName = `Hidden Traders ${id}`;
  await publishOffer(browser, {
    productName,
    city,
    confidential: true,
    businessName,
  });

  // A visitor who never signs in.
  const buyer = await (await browser.newContext()).newPage();
  await buyer.goto("/en");
  await expect(
    buyer.getByRole("heading", { name: en.home.offersHeading }),
  ).toBeVisible();
  await buyer.getByLabel(en.home.searchLabel).fill("detrgent"); // typo on purpose
  await buyer.getByLabel(en.home.searchLabel).press("Enter");
  await expect(buyer).toHaveURL(/\/en\/offers\?q=detrgent/);

  const filters = buyer.getByRole("form", { name: en.offers.filtersHeading });
  await filters.getByLabel(en.offers.city, { exact: true }).selectOption(city);
  await filters.getByRole("button", { name: en.offers.apply }).click();
  await expect(buyer).toHaveURL(
    new RegExp(`city=${encodeURIComponent(city).replaceAll("%20", "\\+")}`),
  );
  const results = buyer
    .getByRole("region", { name: en.offers.resultsHeading })
    .getByRole("listitem");
  await expect(results).toHaveCount(1);
  await expect(results.first()).toContainText(productName);
  await expect(results.first()).toContainText("₹42.00");
  await expectNoA11yViolations(buyer);

  await results.first().getByRole("link").click();
  await expect(
    buyer.getByRole("heading", { level: 1, name: new RegExp(productName) }),
  ).toBeVisible();
  await expect(buyer.getByText(en.offers.confidentialSeller)).toBeVisible();
  await expect(
    buyer.getByRole("heading", { name: en.offers.passport.heading }),
  ).toBeVisible();
  await expect(buyer.getByText(en.offers.passport.notChanged)).toBeVisible();
  await expect(buyer.getByText(businessName)).toHaveCount(0);
  await expect(buyer.getByText("12-3 Main Road")).toHaveCount(0); // never the street address
  await expect(
    buyer.getByRole("button", { name: en.offers.requestQuote }),
  ).toBeDisabled();
  const photos = buyer
    .getByRole("region", { name: en.listings.photosHeading })
    .getByRole("img");
  await expect(photos).toHaveCount(2);
  await expect
    .poll(() =>
      photos.first().evaluate((img: HTMLImageElement) => img.naturalWidth),
    )
    .toBeGreaterThan(0);
  await expectNoA11yViolations(buyer);
});

test("the offers page works in Telugu without signing in", async ({ page }) => {
  await page.goto("/te/offers");
  await expect(page.locator("html")).toHaveAttribute("lang", "te");
  await expect(
    page.getByRole("heading", { level: 1, name: te.offers.title }),
  ).toBeVisible();
  await expectNoA11yViolations(page);
});

test("a question to the assistant opens matching offers, or normal search when it cannot help", async ({
  page,
}) => {
  await page.goto("/te");
  await page.getByRole("button", { name: te.home.assistant.open }).click();
  const dialog = page.getByRole("dialog", { name: te.home.assistant.title });
  await dialog
    .getByLabel(te.home.assistant.label, { exact: false })
    .fill("విజయవాడ దగ్గర 20 కేసుల డిటర్జెంట్");
  await dialog.getByRole("button", { name: te.home.assistant.submit }).click();
  await expect(page).toHaveURL(/\/te\/offers\?asked=/);
  // With an AI key the question becomes removable filters; without one (as in CI), normal search.
  const understood = page.getByRole("region", {
    name: /విజయవాడ దగ్గర 20 కేసుల డిటర్జెంట్/,
  });
  const fallback = page.getByText(
    new RegExp(
      `${te.offers.assistant.fallbackOff}|${te.offers.assistant.fallbackBusy}`,
    ),
  );
  await expect(understood.or(fallback)).toBeVisible();
  await expect(
    page.getByRole("heading", { name: te.offers.resultsHeading }),
  ).toBeVisible();
  await expectNoA11yViolations(page);
});
