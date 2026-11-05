package com.b2bmarket.listings.internal;

import com.b2bmarket.listings.ListingDtos.PriceTier;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "price_tiers")
public class PriceTierEntity {

  @Id private UUID id;

  @Column(name = "listing_id", nullable = false, updatable = false)
  private UUID listingId;

  @Column(name = "business_id", nullable = false, updatable = false)
  private UUID businessId;

  @Column(name = "min_quantity", nullable = false, updatable = false)
  private long minQuantity;

  @Column(name = "unit_price_paise", nullable = false, updatable = false)
  private long unitPricePaise;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected PriceTierEntity() {}

  public static PriceTierEntity create(
      UUID listingId, UUID businessId, PriceTier tier, Instant now) {
    PriceTierEntity entity = new PriceTierEntity();
    entity.id = UUID.randomUUID();
    entity.listingId = listingId;
    entity.businessId = businessId;
    entity.minQuantity = tier.minQuantity();
    entity.unitPricePaise = tier.unitPricePaise();
    entity.createdAt = now;
    return entity;
  }

  public UUID listingId() {
    return listingId;
  }

  public PriceTier toDto() {
    return new PriceTier(minQuantity, unitPricePaise);
  }
}
