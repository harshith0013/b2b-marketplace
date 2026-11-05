package com.b2bmarket.inventory.internal;

import com.b2bmarket.inventory.WarehouseDtos.CreateWarehouseRequest;
import com.b2bmarket.inventory.WarehouseDtos.UpdateWarehouseRequest;
import com.b2bmarket.inventory.WarehouseDtos.Warehouse;
import com.b2bmarket.inventory.WarehouseService;
import com.b2bmarket.users.CurrentUser;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** A business's warehouses. Membership and role checks happen in the service. */
@RestController
@RequestMapping("/api/v1/businesses/{businessId}/warehouses")
class WarehouseController {

  private final WarehouseService warehouses;

  WarehouseController(WarehouseService warehouses) {
    this.warehouses = warehouses;
  }

  @GetMapping
  List<Warehouse> list(CurrentUser caller, @PathVariable UUID businessId) {
    return warehouses.list(caller, businessId);
  }

  @PostMapping
  ResponseEntity<Warehouse> create(
      CurrentUser caller,
      @PathVariable UUID businessId,
      @Valid @RequestBody CreateWarehouseRequest request) {
    Warehouse created = warehouses.create(caller, businessId, request);
    return ResponseEntity.created(
            URI.create("/api/v1/businesses/" + businessId + "/warehouses/" + created.id()))
        .body(created);
  }

  @GetMapping("/{warehouseId}")
  Warehouse get(CurrentUser caller, @PathVariable UUID businessId, @PathVariable UUID warehouseId) {
    return warehouses.get(caller, businessId, warehouseId);
  }

  @PatchMapping("/{warehouseId}")
  Warehouse update(
      CurrentUser caller,
      @PathVariable UUID businessId,
      @PathVariable UUID warehouseId,
      @Valid @RequestBody UpdateWarehouseRequest request) {
    return warehouses.update(caller, businessId, warehouseId, request);
  }
}
