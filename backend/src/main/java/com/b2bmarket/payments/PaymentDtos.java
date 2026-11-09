package com.b2bmarket.payments;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/** Request and response bodies for payouts and online payments. Money in paise. */
public final class PaymentDtos {

  private PaymentDtos() {}

  /**
   * A seller's payout details, sent on to Razorpay to create their linked account. Only the last
   * four digits of the account number are kept here.
   */
  public record PayoutRequest(
      @NotBlank @Email @Size(max = 120) String email,
      @NotBlank @Pattern(regexp = "^[6-9][0-9]{9}$") String phone,
      @NotBlank @Size(min = 4, max = 120) String contactName,
      @NotBlank @Pattern(regexp = "^(proprietorship|partnership|private_limited|llp|individual)$")
          String businessType,
      @NotBlank @Size(min = 4, max = 100) String street,
      @NotBlank @Pattern(regexp = "^[0-9]{9,18}$") String accountNumber,
      @NotBlank @Pattern(regexp = "^[A-Z]{4}0[A-Z0-9]{6}$") String ifsc,
      @NotBlank @Size(min = 4, max = 120) String beneficiaryName,
      @NotNull @AssertTrue Boolean acceptsTerms) {

    @Override
    public String toString() {
      return "PayoutRequest[ifsc=" + ifsc + "]"; // never the account number
    }
  }

  /**
   * A seller's payout account. status: NOT_SET_UP, or Razorpay's activation status. canTakeOnline:
   * buyers may pay this seller online. available: online payment is switched on for the platform.
   */
  public record PayoutAccount(
      String status,
      String bankLast4,
      String ifscCode,
      String beneficiaryName,
      boolean canTakeOnline,
      boolean available) {}

  /** What the website needs to open Razorpay's checkout. The key id is public by design. */
  public record PaymentStart(
      String keyId, String razorpayOrderId, long amountPaise, String currency) {}

  /** What Razorpay's checkout hands back after the buyer pays. */
  public record PaymentConfirm(
      @NotBlank @Size(max = 40) String razorpayOrderId,
      @NotBlank @Size(max = 40) String razorpayPaymentId,
      @NotBlank @Size(max = 128) String razorpaySignature) {}

  /**
   * An order's online payment. status CREATED (waiting), CAPTURED (paid) or REFUNDED;
   * transferStatus: the seller's share NONE, HELD, RELEASED or REVERSED.
   */
  public record Payment(
      String status,
      String transferStatus,
      long amountPaise,
      long platformFeePaise,
      Instant capturedAt) {}
}
