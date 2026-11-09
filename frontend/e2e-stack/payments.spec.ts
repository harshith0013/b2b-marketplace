import { expect, test } from "@playwright/test";
import en from "../src/i18n/dictionaries/en.json";
import {
  createBusiness,
  expectNoA11yViolations,
  newSignedInPage,
  publishOffer,
  uniqueEmail,
} from "./helpers";

const p = en.payouts;
const t = en.orders;

/**
 * Against Razorpay's real test mode (keys from .env): the seller sets up payouts, the buyer
 * chooses online payment, and Razorpay's checkout opens. Paying inside Razorpay's own window is
 * left to a person: its screens are Razorpay's, not ours.
 */
test("a seller sets up payouts and a buyer's online order opens Razorpay's checkout", async ({
  browser,
}) => {
  test.setTimeout(150_000);
  const id = crypto.randomUUID().slice(0, 6);
  const productName = `Floor Cleaner ${id}`;
  const { listingUrl, listingId, seller } = await publishOffer(browser, {
    productName,
    city: "Guntur",
    confidential: false,
    businessName: `Payout Traders ${id}`,
    keepSeller: true,
  });
  if (!seller) throw new Error("seller page expected");
  const sellerBase = listingUrl.replace(/\/listings\/.*$/, "");

  // --- Seller: payouts ------------------------------------------------------------------------
  await seller.goto(`${sellerBase}/payouts`);
  await expect(seller.getByText(p.statuses.NOT_SET_UP)).toBeVisible();
  test.skip(
    await seller.getByText(p.off).isVisible(),
    "Razorpay keys are not set for this stack",
  );
  const form = seller.getByRole("form", { name: p.title });
  await form.getByLabel(p.fields.email).fill(`payouts-${id}@example.com`);
  await form.getByLabel(p.fields.phone).fill("9876543210");
  await form.getByLabel(p.fields.contactName).fill("Ravi Kumar");
  await form.getByLabel(p.fields.businessType).selectOption("proprietorship");
  await form.getByLabel(p.fields.street).fill("12-3 Main Road");
  await form.getByLabel(p.fields.accountNumber).fill("1234567890123456");
  await form.getByLabel(p.fields.ifsc).fill("HDFC0000317");
  await form.getByLabel(p.fields.beneficiaryName).fill("Payout Traders");
  await form.getByLabel(p.fields.acceptsTerms).check();
  await expectNoA11yViolations(seller);
  await form.getByRole("button", { name: p.save }).click();
  // Razorpay decides: in test mode a linked account is usually active at once.
  // Razorpay must enable Route on the platform's own account before sellers can be paid out.
  await expect(form.getByRole("button", { name: p.save })).toBeEnabled({
    timeout: 20_000,
  });
  test.skip(
    await seller.getByText(en.business.errors.payoutsNotEnabled).isVisible(),
    "Route is not enabled on this Razorpay account yet",
  );
  // An error would show inside the form (Next.js's own page announcer is an empty alert too).
  await expect(form.getByRole("alert").filter({ hasText: /\S/ })).toHaveCount(
    0,
    {
      timeout: 20_000,
    },
  );
  await expect(seller.getByText(/1234567890123456/)).toHaveCount(0);
  await expect(
    seller.getByText(
      new RegExp(
        [p.statuses.ACTIVATED, p.statuses.REQUESTED, p.statuses.UNDER_REVIEW]
          .map((s) => s.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"))
          .join("|"),
      ),
    ),
  ).toBeVisible({ timeout: 20_000 });
  const active = await seller.getByText(p.statuses.ACTIVATED).isVisible();
  test.skip(!active, "Razorpay has not activated the test linked account yet");

  // --- Seller: allows online payment on the listing -------------------------------------------
  await seller.goto(listingUrl);
  const delivery = seller.getByRole("form", { name: t.deliveryHeading });
  await delivery.getByLabel(t.onlineOption).check();
  await delivery.getByRole("button", { name: t.saveDelivery }).click();
  await expect(delivery.getByText(t.deliverySaved)).toBeVisible();

  // --- Buyer: orders with online payment ------------------------------------------------------
  const buyer = await newSignedInPage(browser, uniqueEmail("buyer"));
  const buyerPath = await createBusiness(buyer, `Online Kirana ${id}`);
  await buyer.goto(`/en/offers/${listingId}`);
  await buyer.locator("#cart-quantity").fill("20");
  await buyer.getByRole("button", { name: t.addToCart }).click();
  await expect(buyer.getByText(t.added)).toBeVisible();
  await buyer.goto(`${buyerPath}/cart`);
  await buyer.getByRole("radio", { name: t.payOnline }).check();
  await expect(buyer.getByText(t.payNote)).toBeVisible();
  await expectNoA11yViolations(buyer);
  await buyer.getByRole("button", { name: t.placeOrder }).click();
  await expect(buyer).toHaveURL(/\/orders\/[0-9a-f-]{36}$/);
  await expect(
    buyer.getByText(t.paymentMethods.ONLINE, { exact: false }),
  ).toBeVisible();
  const pay = buyer.getByRole("button", { name: /₹/ });
  await expect(pay).toBeVisible();
  await expectNoA11yViolations(buyer);

  // The seller cannot accept before payment.
  const orderPath = new URL(buyer.url()).pathname.replace(buyerPath, "");
  await seller.goto(`${sellerBase}${orderPath}`);
  await expect(seller.getByText(t.waitingForPayment)).toBeVisible();
  await expect(
    seller.getByRole("button", { name: t.actions.ACCEPT }),
  ).toHaveCount(0);

  // --- Razorpay's checkout opens (allowed by our Content-Security-Policy) ---------------------
  await pay.click();
  await expect(
    buyer.locator("iframe[src*='razorpay.com']").first(),
  ).toBeAttached({ timeout: 20_000 });
});
