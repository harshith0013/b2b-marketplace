package com.b2bmarket.inventory;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Request and response bodies of the warehouses API. */
public final class WarehouseDtos {

  static final String PINCODE_PATTERN = "^[1-9][0-9]{5}$";
  static final String PINCODE_MESSAGE = "must be a 6-digit Indian PIN code";

  private WarehouseDtos() {}

  public record CreateWarehouseRequest(
      @NotBlank @Size(max = 100) String name,
      @NotBlank @Size(max = 300) String addressLine,
      @NotBlank @Size(max = 100) String city,
      @NotBlank @Size(max = 100) String state,
      @NotNull @Pattern(regexp = PINCODE_PATTERN, message = PINCODE_MESSAGE) String pincode) {}

  /** Every field is optional; only the fields that are present are changed. */
  public record UpdateWarehouseRequest(
      @Size(min = 1, max = 100) String name,
      @Size(min = 1, max = 300) String addressLine,
      @Size(min = 1, max = 100) String city,
      @Size(min = 1, max = 100) String state,
      @Pattern(regexp = PINCODE_PATTERN, message = PINCODE_MESSAGE) String pincode,
      Boolean active) {}

  public record Warehouse(
      UUID id,
      String name,
      String addressLine,
      String city,
      String state,
      String pincode,
      boolean active) {}
}
