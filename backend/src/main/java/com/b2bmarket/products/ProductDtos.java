package com.b2bmarket.products;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Request and response bodies of the products API. */
public final class ProductDtos {

  static final int MAX_VARIANTS = 10;

  private ProductDtos() {}

  public record CreateProductRequest(
      @NotNull UUID categoryId,
      @NotBlank @Size(max = 100) String brand,
      @NotBlank @Size(max = 200) String name,
      @Size(max = 2000) String description,
      @NotEmpty @Size(max = MAX_VARIANTS) List<@Valid @NotNull VariantRequest> variants) {}

  /** One pack, e.g. 250 G. The barcode (GTIN) is optional but must be valid when given. */
  public record VariantRequest(
      @NotNull @DecimalMin(value = "0.001") @Digits(integer = 9, fraction = 3) BigDecimal packSize,
      @NotNull PackUnit packUnit,
      @Size(max = 20) String gtin) {}

  public record Variant(UUID id, BigDecimal packSize, PackUnit packUnit, String gtin) {}

  public record Product(
      UUID id,
      UUID categoryId,
      String brand,
      String name,
      String description,
      ReviewStatus reviewStatus,
      List<Variant> variants) {

    public Product {
      variants = List.copyOf(variants);
    }
  }

  /** What other modules show about a variant (e.g. on a batch or listing). */
  public record VariantSummary(
      UUID variantId,
      UUID productId,
      UUID categoryId,
      String brand,
      String productName,
      BigDecimal packSize,
      PackUnit packUnit) {}
}
