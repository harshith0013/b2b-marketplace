import AxeBuilder from "@axe-core/playwright";
import { expect, test } from "@playwright/test";
import en from "../src/i18n/dictionaries/en.json";
import hi from "../src/i18n/dictionaries/hi.json";
import te from "../src/i18n/dictionaries/te.json";

const dictionaries = { en, te, hi };
const WCAG_TAGS = ["wcag2a", "wcag2aa", "wcag21a", "wcag21aa", "wcag22aa"];

// Public pages. Signed-in pages (sell, account, business) are covered by e2e-stack/.
const shells = [
  { path: "", heading: (d: typeof en) => d.home.tagline },
  { path: "/offers", heading: (d: typeof en) => d.offers.title },
  { path: "/login", heading: (d: typeof en) => d.auth.loginTitle },
  { path: "/status", heading: (d: typeof en) => d.status.heading },
];

for (const [lang, dict] of Object.entries(dictionaries)) {
  for (const shell of shells) {
    const url = `/${lang}${shell.path}`;

    test(`${url} renders in its language without accessibility violations`, async ({
      page,
    }) => {
      await page.goto(url);

      await expect(page.locator("html")).toHaveAttribute("lang", lang);
      await expect(
        page.getByRole("heading", { level: 1, name: shell.heading(dict) }),
      ).toBeVisible();
      await expect(page.getByText(dict.footer.sampleNotice)).toBeVisible();

      const results = await new AxeBuilder({ page })
        .withTags(WCAG_TAGS)
        .analyze();
      expect(results.violations.map((v) => `${v.id}: ${v.help}`)).toEqual([]);

      // No sideways scrolling on any screen size.
      const overflow = await page.evaluate(
        () =>
          document.documentElement.scrollWidth -
          document.documentElement.clientWidth,
      );
      expect(overflow).toBeLessThanOrEqual(0);
    });
  }
}

test("buyer home: search opens the offers page with the words typed", async ({
  page,
}) => {
  await page.goto("/en");
  await page.getByLabel(en.home.searchLabel).fill("soap");
  await page.getByLabel(en.home.searchLabel).press("Enter");
  await expect(page).toHaveURL(/\/en\/offers\?q=soap$/);
  await expect(
    page.getByRole("heading", { level: 1, name: en.offers.title }),
  ).toBeVisible();
  await expect(page.getByLabel(en.offers.query, { exact: true })).toHaveValue(
    "soap",
  );
});

test("buyer home: without the API the assistant falls back to normal search", async ({
  page,
}) => {
  await page.goto("/en");
  await page.getByRole("button", { name: en.home.assistant.open }).click();
  const dialog = page.getByRole("dialog", { name: en.home.assistant.title });
  await expect(dialog.getByText(en.home.assistant.privacy)).toBeVisible();
  await dialog
    .getByLabel(en.home.assistant.label, { exact: false })
    .fill("20 cases of detergent");
  await dialog.getByRole("button", { name: en.home.assistant.submit }).click();
  await expect(page).toHaveURL(/\/en\/offers\?asked=20/);
  await expect(page.getByText(en.offers.assistant.fallbackOff)).toBeVisible();
  await expect(page.getByLabel(en.offers.query, { exact: true })).toHaveValue(
    "20 cases of detergent",
  );
});

test("buyer home: assistant dialog opens and closes with Escape", async ({
  page,
}) => {
  await page.goto("/en");
  await page.getByRole("button", { name: en.home.assistant.open }).click();
  const dialog = page.getByRole("dialog", { name: en.home.assistant.title });
  await expect(dialog).toBeVisible();
  await page.keyboard.press("Escape");
  await expect(dialog).toBeHidden();
});

// These tests run the website alone, without the API: pages must still work and say so.
test("buyer home and offers say offers are unavailable instead of failing", async ({
  page,
}) => {
  await page.goto("/en");
  await expect(page.getByText(en.home.offersUnavailable)).toBeVisible();
  await page.goto("/en/offers?q=soap&maxPrice=abc");
  await expect(page.getByText(en.offers.invalidFilters)).toBeVisible();
  await expect(page.getByText(en.offers.unavailable)).toBeVisible();
});

for (const path of ["/sell", "/account", "/onboarding", "/admin/reviews"]) {
  test(`${path} sends signed-out visitors to sign in and remembers where they were going`, async ({
    page,
  }) => {
    await page.goto(`/te${path}`);
    await expect(page).toHaveURL(
      `/te/login?returnTo=${encodeURIComponent(`/te${path}`)}`,
    );
    await expect(
      page.getByRole("link", { name: te.auth.continueWithGoogle }),
    ).toHaveAttribute(
      "href",
      `/api/auth/login?lang=te&returnTo=${encodeURIComponent(`/te${path}`)}`,
    );
  });
}

test("a crafted return address cannot send people to another website", async ({
  page,
}) => {
  await page.goto("/en/login?returnTo=https%3A%2F%2Fevil.example");
  await expect(
    page.getByRole("link", { name: en.auth.continueWithGoogle }),
  ).toHaveAttribute(
    "href",
    `/api/auth/login?lang=en&returnTo=${encodeURIComponent("/en/account")}`,
  );
});

test("pages send a Content-Security-Policy that forbids framing", async ({
  request,
}) => {
  const response = await request.get("/en");
  expect(response.headers()["content-security-policy"]).toContain(
    "frame-ancestors 'none'",
  );
});
