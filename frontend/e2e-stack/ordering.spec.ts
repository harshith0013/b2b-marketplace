import { expect, test, type Page } from "@playwright/test";
import en from "../src/i18n/dictionaries/en.json";
import {
  createBusiness,
  expectNoA11yViolations,
  newSignedInPage,
  publishOffer,
  uniqueEmail,
} from "./helpers";

const t = en.orders;

async function step(page: Page, action: keyof typeof t.actions) {
  await page.getByRole("button", { name: t.actions[action] }).click();
}

async function expectStatus(page: Page, status: keyof typeof t.statuses) {
  await expect(
    page.getByText(t.statuses[status], { exact: true }),
  ).toBeVisible();
}

test("a buyer asks for a quote, orders it with delivery, and the seller delivers and is paid", async ({
  browser,
}) => {
  test.setTimeout(150_000);
  const id = crypto.randomUUID().slice(0, 6);
  const productName = `Washing Bar ${id}`;
  const { listingUrl, listingId, seller } = await publishOffer(browser, {
    productName,
    city: "Guntur",
    confidential: false,
    businessName: `Godown Sellers ${id}`,
    keepSeller: true,
  });
  if (!seller) throw new Error("seller page expected");

  // --- Seller: delivers to one PIN code, takes cash on delivery -------------------------------
  await seller.goto(listingUrl);
  const delivery = seller.getByRole("form", { name: t.deliveryHeading });
  await delivery.getByLabel(t.ownDeliveryOption).check();
  await delivery.getByLabel(t.pincodes).fill("522001, 522002");
  await delivery.getByLabel(t.charge).fill("50");
  await delivery.getByLabel(t.codOption).check();
  await delivery.getByRole("button", { name: t.saveDelivery }).click();
  await expect(delivery.getByText(t.deliverySaved)).toBeVisible();

  // --- Buyer: adds to the cart, then asks for a quote -----------------------------------------
  const buyer = await newSignedInPage(browser, uniqueEmail("buyer"));
  const buyerPath = await createBusiness(buyer, `Corner Kirana ${id}`);
  await buyer.goto(`/en/offers/${listingId}`);
  await buyer.locator("#cart-quantity").fill("20");
  await buyer.getByRole("button", { name: t.addToCart }).click();
  await expect(buyer.getByText(t.added)).toBeVisible();
  await expectNoA11yViolations(buyer);

  await buyer.goto(`${buyerPath}/cart`);
  await expect(buyer.getByRole("link", { name: productName })).toBeVisible();
  await expect(buyer.getByText("₹840.00")).toBeVisible(); // 20 × ₹42
  await expectNoA11yViolations(buyer);
  await buyer.getByRole("button", { name: new RegExp(t.remove) }).click();
  await expect(buyer.getByText(t.cartEmpty)).toBeVisible();

  await buyer.goto(`/en/offers/${listingId}`);
  await buyer.locator("#quote-quantity").fill("30");
  await buyer
    .getByLabel(t.quoteNote)
    .fill("Weekly order if the price is right");
  await buyer.getByRole("button", { name: t.askQuote }).click();
  await expect(buyer).toHaveURL(/\/quotes\/[0-9a-f-]{36}$/);
  await expect(
    buyer.getByText(t.quoteStatuses.REQUESTED, { exact: true }),
  ).toBeVisible();
  const quoteUrl = new URL(buyer.url()).pathname;

  // --- Seller: offers ₹40 ---------------------------------------------------------------------
  const sellerBase = listingUrl.replace(/\/listings\/.*$/, "");
  await seller.goto(`${sellerBase}/orders?side=SELLER`);
  await seller.getByRole("link", { name: new RegExp(productName) }).click();
  await expect(
    seller.getByText("Weekly order if the price is right"),
  ).toBeVisible();
  await seller.getByLabel(t.quotePrice).fill("40");
  await seller.getByRole("button", { name: t.offerPrice }).click();
  await expect(
    seller.getByText(t.quoteStatuses.OFFERED, { exact: true }),
  ).toBeVisible();

  // --- Buyer: orders the quote with delivery and cash on delivery -----------------------------
  await buyer.goto(quoteUrl);
  await expect(buyer.getByText("₹40.00", { exact: false })).toBeVisible();
  await buyer.getByRole("radio", { name: t.sellerDelivery }).check();
  await buyer.getByLabel(t.address).fill("4-1 Market Street, Guntur");
  await buyer.getByLabel(t.pincode).fill("522001");
  await expectNoA11yViolations(buyer);
  await buyer.getByRole("button", { name: t.orderQuote }).click();
  await expect(buyer).toHaveURL(/\/orders\/[0-9a-f-]{36}$/);
  const orderPath = new URL(buyer.url()).pathname.replace(buyerPath, "");
  await expectStatus(buyer, "PLACED");
  // 30 × ₹40 at the quoted price, and the seller's delivery charge.
  await expect(
    buyer.getByRole("definition").filter({ hasText: /^₹1,200\.00$/ }),
  ).toBeVisible();
  await expect(
    buyer.getByRole("definition").filter({ hasText: /^₹50\.00$/ }),
  ).toBeVisible();
  await expect(
    buyer.getByRole("button", { name: t.actions.CANCEL }),
  ).toBeVisible();
  await expectNoA11yViolations(buyer);

  // --- Seller: accepts, sends it out, delivers, gets paid -------------------------------------
  await seller.goto(`${sellerBase}${orderPath}`);
  await expectStatus(seller, "PLACED");
  await step(seller, "ACCEPT");
  await expectStatus(seller, "ACCEPTED");
  await seller.getByLabel(t.deliveryPerson).fill("Ravi");
  await seller.getByLabel(t.deliveryPhone).fill("9876543210");
  await expectNoA11yViolations(seller);
  await step(seller, "OUT_FOR_DELIVERY");
  await expectStatus(seller, "OUT_FOR_DELIVERY");
  await step(seller, "DELIVERED");
  await expectStatus(seller, "DELIVERED");
  await step(seller, "PAID");
  await expectStatus(seller, "COMPLETED");

  // --- Buyer sees it completed; a stranger sees nothing ---------------------------------------
  await buyer.reload();
  await expectStatus(buyer, "COMPLETED");
  await expect(buyer.getByText("Ravi", { exact: false })).toBeVisible();

  const stranger = await newSignedInPage(browser, uniqueEmail("stranger"));
  const strangerPath = await createBusiness(stranger, `Elsewhere ${id}`);
  await stranger.goto(`${strangerPath}${orderPath}`);
  await expect(stranger.getByText(/could not be found/i)).toBeVisible();
  await expect(stranger.getByText(productName)).toHaveCount(0);
});
