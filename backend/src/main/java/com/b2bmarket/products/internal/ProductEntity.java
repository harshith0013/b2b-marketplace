package com.b2bmarket.products.internal;

import com.b2bmarket.products.ProductDtos.Product;
import com.b2bmarket.products.ProductDtos.Variant;
import com.b2bmarket.products.ReviewStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "products")
public class ProductEntity {

  @Id private UUID id;

  @Column(name = "category_id", nullable = false)
  private UUID categoryId;

  @Column(nullable = false)
  private String brand;

  @Column(nullable = false)
  private String name;

  private String description;

  @Enumerated(EnumType.STRING)
  @Column(name = "review_status", nullable = false)
  private ReviewStatus reviewStatus;

  @Column(name = "created_by_business_id", updatable = false)
  private UUID createdByBusinessId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected ProductEntity() {}

  public static ProductEntity create(
      UUID categoryId,
      String brand,
      String name,
      String description,
      UUID createdByBusinessId,
      Instant now) {
    ProductEntity product = new ProductEntity();
    product.id = UUID.randomUUID();
    product.categoryId = categoryId;
    product.brand = brand;
    product.name = name;
    product.description = description;
    product.reviewStatus = ReviewStatus.UNREVIEWED;
    product.createdByBusinessId = createdByBusinessId;
    product.createdAt = now;
    product.updatedAt = now;
    return product;
  }

  public UUID id() {
    return id;
  }

  public UUID categoryId() {
    return categoryId;
  }

  public String brand() {
    return brand;
  }

  public String name() {
    return name;
  }

  /** Approved products are visible to everyone; others only to the business that added them. */
  public boolean visibleTo(Collection<UUID> businessIds) {
    return reviewStatus == ReviewStatus.APPROVED || businessIds.contains(createdByBusinessId);
  }

  public Product toDto(List<Variant> variants) {
    return new Product(id, categoryId, brand, name, description, reviewStatus, variants);
  }
}
