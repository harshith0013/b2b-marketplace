import { expect, test, type Page } from "@playwright/test";
import en from "../src/i18n/dictionaries/en.json";
import {
  addWarehouse,
  approveAsReviewer,
  waitForLabelReading,
  createBusiness,
  expectNoA11yViolations,
  newSignedInPage,
  isoDateInDays,
  REVIEWER,
  uniqueEmail,
  uploadListingPhoto,
} from "./helpers";

/** The wizard's form; Next.js keeps earlier pages hidden in the DOM, so look only inside it. */
function wizard(page: Page) {
  return page.getByRole("form", { name: en.wizard.title });
}

async function next(page: Page) {
  await page.getByRole("button", { name: new RegExp(en.common.next) }).click();
}

test("a seller lists stock, a reviewer sends it back, then approves it", async ({
  browser,
}) => {
  test.setTimeout(90_000);
  const productName = `Neem Soap ${crypto.randomUUID().slice(0, 8)}`;

  // --- Seller: business and warehouse --------------------------------------------------------
  const seller = await newSignedInPage(browser, uniqueEmail("seller"));
  const businessPath = await createBusiness(seller, "Clearance Traders");
  await seller.goto(`${businessPath}/warehouses`);
  await addWarehouse(seller, "Main godown");
  await expect(
    seller.getByRole("status").filter({ hasText: en.warehouses.added }),
  ).toBeVisible();

  // --- Seller: the wizard --------------------------------------------------------------------
  await seller.goto(businessPath);
  await seller.getByRole("link", { name: en.business.listingsLink }).click();
  await expect(seller.getByText(en.listings.none)).toBeVisible();
  await expectNoA11yViolations(seller);
  await seller.getByRole("link", { name: en.listings.newListing }).click();

  const form = wizard(seller);
  // Approved products from earlier runs may exist, so choose "new" explicitly.
  await form.getByRole("radio", { name: en.wizard.product.new }).check();
  await form
    .getByLabel(en.wizard.product.category, { exact: true })
    .selectOption({ label: "Personal care › Bath soaps and body wash" });
  await form
    .getByLabel(en.wizard.product.brand, { exact: true })
    .fill("Test Brand");
  await form
    .getByLabel(en.wizard.product.name, { exact: true })
    .fill(productName);
  await form
    .getByLabel(en.wizard.product.packSize, { exact: true })
    .fill("100");
  await expectNoA11yViolations(seller);
  await next(seller);

  await form
    .getByLabel(en.wizard.batch.warehouse, { exact: true })
    .selectOption({ index: 1 });
  await form
    .getByLabel(en.wizard.batch.batchNumber, { exact: true })
    .fill("B-2611");
  await form
    .getByLabel(en.wizard.batch.expiresOn, { exact: true })
    .fill(isoDateInDays(60));
  await form.getByLabel(en.wizard.batch.mrp, { exact: true }).fill("45");
  await form.getByLabel(en.wizard.batch.quantity, { exact: true }).fill("500");
  await expectNoA11yViolations(seller);
  await next(seller);

  await form.getByLabel(en.wizard.price.moq, { exact: true }).fill("10");
  await form.getByLabel(en.wizard.price.tierPrice, { exact: true }).fill("30");
  await expectNoA11yViolations(seller);
  await next(seller);
  await seller.getByRole("button", { name: en.wizard.save }).click();

  // --- Seller: photos and submit -------------------------------------------------------------
  await expect(seller).toHaveURL(/\/listings\/[0-9a-f-]{36}$/);
  const listingUrl = seller.url();
  await expect(
    seller.getByText(en.labels.problems.PACK_PHOTO_MISSING),
  ).toBeVisible();
  await expect(
    seller.getByText(en.labels.problems.LABEL_PHOTO_MISSING),
  ).toBeVisible();
  await expect(
    seller.getByRole("button", { name: en.listings.submit }),
  ).toBeDisabled();
  await expectNoA11yViolations(seller);

  await uploadListingPhoto(seller, "pack-front.png");
  await uploadListingPhoto(seller, "label-date.png");
  await waitForLabelReading(seller);
  await expect(seller.getByText(en.listings.ready)).toBeVisible();
  const photos = seller
    .getByRole("region", { name: en.listings.photosHeading })
    .getByRole("img");
  await expect(photos).toHaveCount(2);
  // The images really load, through this site's own route.
  await expect
    .poll(() =>
      photos.first().evaluate((img: HTMLImageElement) => img.naturalWidth),
    )
    .toBeGreaterThan(0);
  await expectNoA11yViolations(seller);

  await seller.getByRole("button", { name: en.listings.submit }).click();
  await expect(
    seller.getByText(en.labels.listingStatuses.PENDING_REVIEW, { exact: true }),
  ).toBeVisible();
  await expect(seller.getByText(en.listings.inReviewNote)).toBeVisible();

  // Sellers cannot open the review queue.
  await seller.goto("/en/admin/reviews");
  await expect(seller.getByText(en.review.notAllowed)).toBeVisible();

  // --- Reviewer: send it back ----------------------------------------------------------------
  const reviewer = await newSignedInPage(browser, REVIEWER);
  await expect(
    reviewer.getByRole("link", { name: en.account.reviewQueue }),
  ).toBeVisible();
  await reviewer.getByRole("link", { name: en.account.reviewQueue }).click();
  await expectNoA11yViolations(reviewer);
  await reviewer.getByRole("link", { name: new RegExp(productName) }).click();
  // With an AI key the plain test images read as "unreadable" (a flag); without one, no flags.
  await expect(
    reviewer.getByRole("heading", { name: en.review.labelHeading }),
  ).toBeVisible();
  const reviewPhotos = reviewer
    .getByRole("region", { name: en.listings.photosHeading })
    .getByRole("img");
  await expect(reviewPhotos).toHaveCount(2);
  await expect
    .poll(() =>
      reviewPhotos
        .first()
        .evaluate((img: HTMLImageElement) => img.naturalWidth),
    )
    .toBeGreaterThan(0);
  await expectNoA11yViolations(reviewer);

  await reviewer
    .getByLabel(en.review.reason, { exact: true })
    .fill("The batch number is not visible in the label photo.");
  await reviewer.getByRole("button", { name: en.review.reject }).click();
  await expect(reviewer).toHaveURL(/\/en\/admin\/reviews$/);

  // --- Seller: sees why, resubmits -----------------------------------------------------------
  await seller.goto(listingUrl);
  await expect(
    seller.getByRole("heading", { name: en.listings.rejectedHeading }),
  ).toBeVisible();
  await expect(
    seller.getByText("The batch number is not visible"),
  ).toBeVisible();
  await seller.getByRole("button", { name: en.listings.submit }).click();
  await expect(seller.getByText(en.listings.inReviewNote)).toBeVisible();

  // --- Reviewer: approves --------------------------------------------------------------------
  await reviewer.goto("/en/admin/reviews");
  await reviewer.getByRole("link", { name: new RegExp(productName) }).click();
  await expect(reviewer.getByText(en.review.decisions.REJECTED)).toBeVisible(); // history
  await approveAsReviewer(reviewer);
  await expect(reviewer).toHaveURL(/\/en\/admin\/reviews$/);

  await seller.goto(listingUrl);
  await expect(seller.getByText(en.listings.publishedNote)).toBeVisible();
  await seller.getByRole("button", { name: en.listings.pause }).click();
  await expect(seller.getByText(en.listings.pausedNote)).toBeVisible();

  // --- Another business sees nothing ---------------------------------------------------------
  const stranger = await newSignedInPage(browser, uniqueEmail("stranger"));
  await stranger.goto(listingUrl);
  await expect(stranger.getByText(/could not be found/i)).toBeVisible();
  await expect(stranger.getByText(productName)).toHaveCount(0);
  const photoSrc = await photos
    .first()
    .getAttribute("src")
    .catch(() => null);
  if (photoSrc) {
    const response = await stranger.request.get(photoSrc);
    expect(response.status()).toBe(404);
  }
});

test("the seller hub sends people without a business to onboarding", async ({
  browser,
}) => {
  const page = await newSignedInPage(
    browser,
    uniqueEmail("newcomer"),
    "/en/sell",
  );
  await expect(page.getByText(en.sell.noBusinessTitle)).toBeVisible();
  await expect(
    page.getByRole("link", { name: en.sell.createBusiness }),
  ).toHaveAttribute("href", "/en/onboarding");
  await expectNoA11yViolations(page);
});
