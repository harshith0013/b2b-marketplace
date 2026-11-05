package com.b2bmarket.inventory;

import com.b2bmarket.auditing.AuditEntry;
import com.b2bmarket.auditing.AuditService;
import com.b2bmarket.businesses.BusinessRole;
import com.b2bmarket.businesses.TenantAccess;
import com.b2bmarket.inventory.WarehouseDtos.CreateWarehouseRequest;
import com.b2bmarket.inventory.WarehouseDtos.UpdateWarehouseRequest;
import com.b2bmarket.inventory.WarehouseDtos.Warehouse;
import com.b2bmarket.inventory.internal.WarehouseEntity;
import com.b2bmarket.inventory.internal.WarehouseEntity.Fields;
import com.b2bmarket.inventory.internal.WarehouseRepository;
import com.b2bmarket.platform.ApiException;
import com.b2bmarket.platform.Text;
import com.b2bmarket.users.CurrentUser;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A business's warehouses. Members can see them; owners and managers can add and change them.
 * Warehouses are never deleted (stock batches point at them); they are deactivated instead.
 */
@Service
public class WarehouseService {

  static final String TARGET_TYPE = "warehouse";
  static final String NOT_FOUND = "Warehouse not found.";

  /** Keeps the unpaged list small. */
  static final int MAX_WAREHOUSES = 50;

  private final WarehouseRepository warehouses;
  private final TenantAccess tenants;
  private final AuditService audit;
  private final Clock clock;

  WarehouseService(
      WarehouseRepository warehouses, TenantAccess tenants, AuditService audit, Clock clock) {
    this.warehouses = warehouses;
    this.tenants = tenants;
    this.audit = audit;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public List<Warehouse> list(CurrentUser caller, UUID businessId) {
    tenants.enter(caller, businessId, BusinessRole.ANY);
    return warehouses.findByBusinessIdOrderByNameAsc(businessId).stream()
        .map(WarehouseEntity::toDto)
        .toList();
  }

  @Transactional(readOnly = true)
  public Warehouse get(CurrentUser caller, UUID businessId, UUID warehouseId) {
    tenants.enter(caller, businessId, BusinessRole.ANY);
    return load(businessId, warehouseId).toDto();
  }

  @Transactional
  public Warehouse create(CurrentUser caller, UUID businessId, CreateWarehouseRequest request) {
    tenants.enter(caller, businessId, BusinessRole.OWNER_OR_MANAGER);
    if (warehouses.countByBusinessId(businessId) >= MAX_WAREHOUSES) {
      throw ApiException.conflict("A business can have at most " + MAX_WAREHOUSES + " warehouses.");
    }
    Fields fields =
        new Fields(
            Text.required(request.name(), "Name"),
            Text.required(request.addressLine(), "Address"),
            Text.required(request.city(), "City"),
            Text.required(request.state(), "State"),
            request.pincode(),
            true);
    if (warehouses.existsByBusinessIdAndNameIgnoreCase(businessId, fields.name())) {
      throw nameTaken();
    }
    WarehouseEntity warehouse =
        warehouses.save(WarehouseEntity.create(businessId, fields, Instant.now(clock)));
    audit.record(
        new AuditEntry(
            "warehouse.created",
            caller.userId(),
            businessId,
            TARGET_TYPE,
            warehouse.id(),
            Map.of("pincode", fields.pincode())));
    return warehouse.toDto();
  }

  @Transactional
  public Warehouse update(
      CurrentUser caller, UUID businessId, UUID warehouseId, UpdateWarehouseRequest request) {
    tenants.enter(caller, businessId, BusinessRole.OWNER_OR_MANAGER);
    WarehouseEntity warehouse = load(businessId, warehouseId);
    Fields before = warehouse.fields();
    Fields after = merge(before, request);
    if (!before.name().equalsIgnoreCase(after.name())
        && warehouses.existsByBusinessIdAndNameIgnoreCaseAndIdNot(
            businessId, after.name(), warehouseId)) {
      throw nameTaken();
    }
    List<String> changed = changedFields(before, after);
    if (!changed.isEmpty()) {
      warehouse.apply(after, Instant.now(clock));
      audit.record(
          new AuditEntry(
              "warehouse.updated",
              caller.userId(),
              businessId,
              TARGET_TYPE,
              warehouseId,
              Map.of("changedFields", changed)));
    }
    return warehouse.toDto();
  }

  /**
   * For stock batches: the warehouse must belong to the business and be active. 400 otherwise. Call
   * inside a transaction already scoped to the business.
   */
  @Transactional(readOnly = true)
  public void requireActive(UUID businessId, UUID warehouseId) {
    warehouses
        .findByIdAndBusinessId(warehouseId, businessId)
        .filter(WarehouseEntity::active)
        .orElseThrow(() -> ApiException.badRequest("Choose one of your active warehouses."));
  }

  private WarehouseEntity load(UUID businessId, UUID warehouseId) {
    return warehouses
        .findByIdAndBusinessId(warehouseId, businessId)
        .orElseThrow(() -> ApiException.notFound(NOT_FOUND));
  }

  private static Fields merge(Fields current, UpdateWarehouseRequest request) {
    return new Fields(
        request.name() == null ? current.name() : Text.required(request.name(), "Name"),
        request.addressLine() == null
            ? current.addressLine()
            : Text.required(request.addressLine(), "Address"),
        request.city() == null ? current.city() : Text.required(request.city(), "City"),
        request.state() == null ? current.state() : Text.required(request.state(), "State"),
        request.pincode() == null ? current.pincode() : request.pincode(),
        request.active() == null ? current.active() : request.active());
  }

  static List<String> changedFields(Fields before, Fields after) {
    List<String> changed = new ArrayList<>();
    addIfChanged(changed, "name", before.name(), after.name());
    addIfChanged(changed, "addressLine", before.addressLine(), after.addressLine());
    addIfChanged(changed, "city", before.city(), after.city());
    addIfChanged(changed, "state", before.state(), after.state());
    addIfChanged(changed, "pincode", before.pincode(), after.pincode());
    addIfChanged(changed, "active", before.active(), after.active());
    return List.copyOf(changed);
  }

  private static void addIfChanged(List<String> changed, String name, Object before, Object after) {
    if (!Objects.equals(before, after)) {
      changed.add(name);
    }
  }

  private static ApiException nameTaken() {
    return ApiException.conflict("You already have a warehouse with this name.");
  }
}
