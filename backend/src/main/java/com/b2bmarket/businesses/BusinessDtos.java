package com.b2bmarket.businesses;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Request and response bodies of the businesses API. */
public final class BusinessDtos {

  static final String PINCODE_PATTERN = "^[1-9][0-9]{5}$";
  static final String PINCODE_MESSAGE = "must be a 6-digit Indian PIN code";

  private BusinessDtos() {}

  public record CreateBusinessRequest(
      @NotBlank @Size(max = 200) String legalName,
      @Size(max = 200) String tradeName,
      @NotNull BusinessType businessType,
      @Size(max = 15) String gstin,
      @NotBlank @Size(max = 100) String city,
      @NotBlank @Size(max = 100) String state,
      @NotNull @Pattern(regexp = PINCODE_PATTERN, message = PINCODE_MESSAGE) String pincode) {}

  /** Every field is optional; only the fields that are present are changed. */
  public record UpdateBusinessRequest(
      @Size(min = 1, max = 200) String legalName,
      @Size(max = 200) String tradeName,
      BusinessType businessType,
      @Size(max = 15) String gstin,
      @Size(min = 1, max = 100) String city,
      @Size(min = 1, max = 100) String state,
      @Pattern(regexp = PINCODE_PATTERN, message = PINCODE_MESSAGE) String pincode) {}

  /** A business as seen by one of its members, including that member's own role. */
  public record BusinessDetails(
      UUID id,
      String legalName,
      String tradeName,
      BusinessType businessType,
      String gstin,
      String city,
      String state,
      String pincode,
      String verificationStatus,
      BusinessRole yourRole) {}

  public record Member(UUID userId, String email, String displayName, BusinessRole role) {}

  /** One business the signed-in person belongs to. */
  public record Membership(UUID businessId, String name, BusinessRole role) {}
}
