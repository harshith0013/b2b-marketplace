package com.b2bmarket.inventory.internal;

import com.b2bmarket.inventory.WarehouseDtos.Warehouse;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "warehouses")
public class WarehouseEntity {

  @Id private UUID id;

  @Column(name = "business_id", nullable = false, updatable = false)
  private UUID businessId;

  @Column(nullable = false)
  private String name;

  @Column(name = "address_line", nullable = false)
  private String addressLine;

  @Column(nullable = false)
  private String city;

  @Column(nullable = false)
  private String state;

  @Column(nullable = false)
  private String pincode;

  @Column(nullable = false)
  private boolean active;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected WarehouseEntity() {}

  /** Validated, trimmed field values. */
  public record Fields(
      String name, String addressLine, String city, String state, String pincode, boolean active) {}

  public static WarehouseEntity create(UUID businessId, Fields fields, Instant now) {
    WarehouseEntity warehouse = new WarehouseEntity();
    warehouse.id = UUID.randomUUID();
    warehouse.businessId = businessId;
    warehouse.createdAt = now;
    warehouse.apply(fields, now);
    return warehouse;
  }

  public void apply(Fields fields, Instant now) {
    name = fields.name();
    addressLine = fields.addressLine();
    city = fields.city();
    state = fields.state();
    pincode = fields.pincode();
    active = fields.active();
    updatedAt = now;
  }

  public Fields fields() {
    return new Fields(name, addressLine, city, state, pincode, active);
  }

  public UUID id() {
    return id;
  }

  public UUID businessId() {
    return businessId;
  }

  public boolean active() {
    return active;
  }

  public Warehouse toDto() {
    return new Warehouse(id, name, addressLine, city, state, pincode, active);
  }
}
