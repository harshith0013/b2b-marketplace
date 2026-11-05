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
  { path: "/login", heading: (d: typeof en) => d.auth.loginTitle },
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

for (const path of ["/sell", "/sell/new", "/account", "/onboarding"]) {
  test(`${path} sends signed-out visitors to sign in and remembers where they were going`, async ({
    page,
  }) => {
    await page.goto(`/te${path}`);
    await expect(page).toHaveURL(`/te/login?returnTo=${encodeURIComponent(`/te${path}`)}`);
    await expect(page.getByRole("link", { name: te.auth.continueWithGoogle })).toHaveAttribute(
      "href",
      `/api/auth/login?lang=te&returnTo=${encodeURIComponent(`/te${path}`)}`,
    );
  });
}

test("a crafted return address cannot send people to another website", async ({ page }) => {
  await page.goto("/en/login?returnTo=https%3A%2F%2Fevil.example");
  await expect(page.getByRole("link", { name: en.auth.continueWithGoogle })).toHaveAttribute(
    "href",
    `/api/auth/login?lang=en&returnTo=${encodeURIComponent("/en/account")}`,
  );
});

test("pages send a Content-Security-Policy that forbids framing", async ({ request }) => {
  const response = await request.get("/en");
  expect(response.headers()["content-security-policy"]).toContain("frame-ancestors 'none'");
});
