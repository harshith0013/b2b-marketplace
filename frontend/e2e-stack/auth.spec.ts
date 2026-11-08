import { expect, test } from "@playwright/test";
import en from "../src/i18n/dictionaries/en.json";
import te from "../src/i18n/dictionaries/te.json";
import {
  addWarehouse,
  createBusiness,
  expectNoA11yViolations,
  newSignedInPage,
  signIn,
  uniqueEmail,
} from "./helpers";

test("a new person signs in, creates a business and finds it on their account", async ({
  page,
}) => {
  const email = uniqueEmail("asha");
  await signIn(page, email);

  await expect(page.getByText(email)).toBeVisible();
  await expect(page.getByText(en.account.noBusinesses)).toBeVisible();
  await expectNoA11yViolations(page);

  await page.getByRole("link", { name: en.account.createBusiness }).click();
  await expect(page).toHaveURL(/\/en\/onboarding$/);
  await expectNoA11yViolations(page);

  const businessPath = await createBusiness(page, "Asha Kirana Stores");
  await expect(
    page.getByText(`${en.business.yourRole}: ${en.account.roles.OWNER}`),
  ).toBeVisible();
  await expectNoA11yViolations(page);

  await page.goto("/en/account");
  await expect(
    page.getByRole("link", { name: /Asha Kirana Stores/ }),
  ).toHaveAttribute("href", businessPath);
});

test("an invited person accepts and joins with a limited role", async ({
  browser,
}) => {
  const owner = await newSignedInPage(browser, uniqueEmail("owner"));
  const businessPath = await createBusiness(owner, "Ravi Wholesale");
  const staffEmail = uniqueEmail("staff");

  await owner.getByLabel(en.business.inviteEmail).fill(staffEmail);
  await owner.getByRole("button", { name: en.business.invite }).click();
  await expect(
    owner.getByRole("status").filter({ hasText: en.business.invited }),
  ).toBeVisible();
  await expect(owner.getByText(staffEmail)).toBeVisible();

  const staff = await newSignedInPage(browser, staffEmail);
  await expect(staff.getByText(/Ravi Wholesale/)).toBeVisible();
  await staff.getByRole("button", { name: en.account.accept }).click();
  await expect(
    staff.getByRole("link", { name: /Ravi Wholesale/ }),
  ).toBeVisible();

  await staff.goto(businessPath);
  await expect(
    staff.getByText(`${en.business.yourRole}: ${en.account.roles.STAFF}`),
  ).toBeVisible();
  // Staff see the details but cannot edit them or manage the team.
  await expect(
    staff.getByRole("button", { name: en.business.save }),
  ).toHaveCount(0);
  await expect(
    staff.getByRole("heading", { name: en.business.inviteHeading }),
  ).toHaveCount(0);

  await owner.reload();
  await expect(owner.getByText(staffEmail)).toBeVisible(); // now listed as a member
  await expect(owner.getByText(en.business.noInvitations)).toBeVisible();
});

test("another business cannot open this business's page", async ({
  browser,
}) => {
  const owner = await newSignedInPage(browser, uniqueEmail("owner"));
  const businessPath = await createBusiness(owner, "Private Traders");

  const stranger = await newSignedInPage(browser, uniqueEmail("stranger"));
  await stranger.goto(businessPath);
  // The page streams, so the HTTP status is already sent; Next.js swaps in its not-found page.
  await expect(stranger.getByText(/could not be found/i)).toBeVisible();
  await expect(stranger.getByText("Private Traders")).toHaveCount(0);
  await expect(
    stranger.getByRole("heading", { name: en.business.detailsHeading }),
  ).toHaveCount(0);
});

test("signing out ends the session", async ({ page }) => {
  await signIn(page, uniqueEmail("leaving"));
  await page.getByRole("button", { name: en.auth.signOut }).click();
  await expect(page).toHaveURL(/\/en$/);
  await expect(page.getByRole("link", { name: en.auth.signIn })).toBeVisible();

  await page.goto("/en/account");
  await expect(page).toHaveURL(/\/en\/login\?returnTo=/);
});

test("sign-in and the account page work in Telugu", async ({ page }) => {
  await signIn(page, uniqueEmail("telugu"), "/te/account", te);
  await expect(page.locator("html")).toHaveAttribute("lang", "te");
  await expect(
    page.getByRole("heading", { level: 1, name: te.account.title }),
  ).toBeVisible();
  await expectNoA11yViolations(page);
});

test("owners add and switch off warehouses; strangers get nothing", async ({
  browser,
}) => {
  const owner = await newSignedInPage(browser, uniqueEmail("owner"));
  const businessPath = await createBusiness(owner, "Godown Traders");

  await owner.getByRole("link", { name: en.business.warehousesLink }).click();
  await expect(owner).toHaveURL(new RegExp(`${businessPath}/warehouses$`));
  await expect(owner.getByText(en.warehouses.none)).toBeVisible();
  await expectNoA11yViolations(owner);

  await addWarehouse(owner, "Guntur depot");
  await expect(
    owner.getByRole("status").filter({ hasText: en.warehouses.added }),
  ).toBeVisible();
  await expect(owner.getByText("Guntur depot", { exact: true })).toBeVisible();
  await expectNoA11yViolations(owner);

  // The same name again (in any letter case) is refused.
  await addWarehouse(owner, "guntur DEPOT");
  await expect(
    owner
      .getByRole("alert")
      .filter({ hasText: en.business.errors.warehouseNameTaken }),
  ).toBeVisible();

  await owner
    .getByRole("button", { name: new RegExp(en.warehouses.deactivate) })
    .click();
  await expect(owner.getByText(en.warehouses.inactive)).toBeVisible();

  const stranger = await newSignedInPage(browser, uniqueEmail("stranger"));
  await stranger.goto(`${businessPath}/warehouses`);
  await expect(stranger.getByText(/could not be found/i)).toBeVisible();
  await expect(stranger.getByText("Guntur depot", { exact: true })).toHaveCount(
    0,
  );
});
