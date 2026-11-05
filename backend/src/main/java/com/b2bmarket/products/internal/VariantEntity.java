package com.b2bmarket.products.internal;

import com.b2bmarket.products.PackUnit;
import com.b2bmarket.products.ProductDtos.Variant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "product_variants")
public class VariantEntity {

  @Id private UUID id;

  @Column(name = "product_id", nullable = false, updatable = false)
  private UUID productId;

  @Column(name = "pack_size", nullable = false, precision = 12, scale = 3)
  private BigDecimal packSize;

  @Enumerated(EnumType.STRING)
  @Column(name = "pack_unit", nullable = false)
  private PackUnit packUnit;

  private String gtin;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected VariantEntity() {}

  /** Validated, normalized values for a new variant. */
  public record Fields(BigDecimal packSize, PackUnit packUnit, String gtin) {}

  public static VariantEntity create(UUID productId, Fields fields, Instant now) {
    VariantEntity variant = new VariantEntity();
    variant.id = UUID.randomUUID();
    variant.productId = productId;
    variant.packSize = fields.packSize();
    variant.packUnit = fields.packUnit();
    variant.gtin = fields.gtin();
    variant.createdAt = now;
    variant.updatedAt = now;
    return variant;
  }

  public UUID id() {
    return id;
  }

  public UUID productId() {
    return productId;
  }

  public BigDecimal packSize() {
    return packSize;
  }

  public PackUnit packUnit() {
    return packUnit;
  }

  public Variant toDto() {
    return new Variant(id, packSize.stripTrailingZeros(), packUnit, gtin);
  }
}
