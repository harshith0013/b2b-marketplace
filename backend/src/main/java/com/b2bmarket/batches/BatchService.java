package com.b2bmarket.batches;

import com.b2bmarket.auditing.AuditEntry;
import com.b2bmarket.auditing.AuditService;
import com.b2bmarket.batches.BatchDtos.Batch;
import com.b2bmarket.batches.BatchDtos.BatchSummary;
import com.b2bmarket.batches.BatchDtos.CreateBatchRequest;
import com.b2bmarket.batches.BatchDtos.UpdateBatchRequest;
import com.b2bmarket.batches.internal.BatchEntity;
import com.b2bmarket.batches.internal.BatchEntity.Fields;
import com.b2bmarket.batches.internal.BatchRepository;
import com.b2bmarket.businesses.BusinessRole;
import com.b2bmarket.businesses.TenantAccess;
import com.b2bmarket.inventory.WarehouseService;
import com.b2bmarket.platform.ApiException;
import com.b2bmarket.platform.PageResult;
import com.b2bmarket.products.ProductDtos.VariantSummary;
import com.b2bmarket.products.ProductService;
import com.b2bmarket.users.CurrentUser;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A business's stock batches. Members can see them; owners and managers can add and change them.
 * Once a batch is approved, changing its batch number or dates sends it back for review and is
 * recorded in the audit log; the database refuses any other way of changing them.
 */
@Service
public class BatchService {

  static final String TARGET_TYPE = "batch";
  static final String NOT_FOUND = "Batch not found.";

  /** Soonest expiry first: that stock most needs clearing. */
  static final Sort LIST_ORDER = Sort.by("expiresOn", "id");

  private final BatchRepository batches;
  private final ProductService products;
  private final WarehouseService warehouses;
  private final TenantAccess tenants;
  private final AuditService audit;
  private final Clock clock;

  BatchService(
      BatchRepository batches,
      ProductService products,
      WarehouseService warehouses,
      TenantAccess tenants,
      AuditService audit,
      Clock clock) {
    this.batches = batches;
    this.products = products;
    this.warehouses = warehouses;
    this.tenants = tenants;
    this.audit = audit;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public PageResult<Batch> list(CurrentUser caller, UUID businessId, Integer page, Integer size) {
    tenants.enter(caller, businessId, BusinessRole.ANY);
    Page<BatchEntity> found =
        batches.findByBusinessId(businessId, PageResult.request(page, size, LIST_ORDER));
    Map<UUID, VariantSummary> variants =
        products.summaries(found.getContent().stream().map(BatchEntity::variantId).toList());
    return PageResult.of(found, b -> toDto(b, variants.get(b.variantId())));
  }

  @Transactional(readOnly = true)
  public Batch get(CurrentUser caller, UUID businessId, UUID batchId) {
    tenants.enter(caller, businessId, BusinessRole.ANY);
    return withVariant(load(businessId, batchId));
  }

  @Transactional
  public Batch create(CurrentUser caller, UUID businessId, CreateBatchRequest request) {
    tenants.enter(caller, businessId, BusinessRole.OWNER_OR_MANAGER);
    VariantSummary variant = products.requireUsableVariant(businessId, request.variantId());
    warehouses.requireActive(businessId, request.warehouseId());
    Integer packsPerCase = BatchRules.packsPerCase(request.quantityUnit(), request.packsPerCase());
    Fields fields =
        new Fields(
            request.warehouseId(),
            BatchRules.batchNumber(request.batchNumber()),
            request.manufacturedOn(),
            request.expiresOn(),
            request.dateType(),
            request.quantity(),
            request.mrpPaise());
    BatchRules.requireDates(fields.manufacturedOn(), fields.expiresOn(), clock);
    if (batches.existsByBusinessIdAndVariantIdAndBatchNumberIgnoreCase(
        businessId, variant.variantId(), fields.batchNumber())) {
      throw duplicateBatch();
    }

    BatchEntity batch =
        batches.save(
            BatchEntity.create(
                businessId,
                variant.variantId(),
                request.quantityUnit(),
                packsPerCase,
                fields,
                Instant.now(clock)));
    audit.record(
        new AuditEntry(
            "batch.created",
            caller.userId(),
            businessId,
            TARGET_TYPE,
            batch.id(),
            Map.of(
                "variantId", variant.variantId().toString(),
                "expiresOn", fields.expiresOn().toString(),
                "quantity", fields.quantity(),
                "quantityUnit", request.quantityUnit().name())));
    return toDto(batch, variant);
  }

  @Transactional
  public Batch update(
      CurrentUser caller, UUID businessId, UUID batchId, UpdateBatchRequest request) {
    tenants.enter(caller, businessId, BusinessRole.OWNER_OR_MANAGER);
    BatchEntity batch = load(businessId, batchId);
    Fields before = batch.fields();
    Fields after = merge(before, request);

    if (!after.warehouseId().equals(before.warehouseId())) {
      warehouses.requireActive(businessId, after.warehouseId());
    }
    boolean printedChanged = after.printedDetailsDifferFrom(before);
    if (printedChanged) {
      BatchRules.requireDates(after.manufacturedOn(), after.expiresOn(), clock);
      if (!after.batchNumber().equalsIgnoreCase(before.batchNumber())
          && batches.existsByBusinessIdAndVariantIdAndBatchNumberIgnoreCaseAndIdNot(
              businessId, batch.variantId(), after.batchNumber(), batchId)) {
        throw duplicateBatch();
      }
    }
    List<String> changed = changedFields(before, after);
    if (changed.isEmpty()) {
      return withVariant(batch);
    }

    boolean reReview = printedChanged && batch.status() == BatchStatus.APPROVED;
    batch.apply(after, Instant.now(clock));
    if (reReview) {
      batch.sendBackForReview();
    }
    audit.record(
        new AuditEntry(
            reReview ? "batch.approved_details_changed" : "batch.updated",
            caller.userId(),
            businessId,
            TARGET_TYPE,
            batchId,
            auditDetails(before, after, changed)));
    return withVariant(batch);
  }

  /**
   * For listings: a batch of this business that can still be sold (not expired, not rejected). 400
   * otherwise. Call inside a transaction already scoped to the business.
   */
  @Transactional(readOnly = true)
  public BatchSummary requireSellable(UUID businessId, UUID batchId) {
    BatchEntity batch =
        batches
            .findByIdAndBusinessId(batchId, businessId)
            .orElseThrow(() -> ApiException.badRequest("Choose one of your stock batches."));
    if (batch.status() == BatchStatus.REJECTED) {
      throw ApiException.badRequest("This batch was rejected in review and cannot be listed.");
    }
    if (batch.fields().expiresOn().isBefore(BatchRules.today(clock))) {
      throw ApiException.badRequest("This batch has expired and cannot be listed.");
    }
    return toSummary(batch, products.summaries(List.of(batch.variantId())).get(batch.variantId()));
  }

  /** Summaries of this business's batches, keyed by id. Call inside a scoped transaction. */
  @Transactional(readOnly = true)
  public Map<UUID, BatchSummary> summaries(UUID businessId, Collection<UUID> batchIds) {
    if (batchIds.isEmpty()) {
      return Map.of();
    }
    List<BatchEntity> found = batches.findByBusinessIdAndIdIn(businessId, batchIds);
    Map<UUID, VariantSummary> variants =
        products.summaries(found.stream().map(BatchEntity::variantId).toList());
    return found.stream()
        .collect(Collectors.toMap(BatchEntity::id, b -> toSummary(b, variants.get(b.variantId()))));
  }

  private BatchEntity load(UUID businessId, UUID batchId) {
    return batches
        .findByIdAndBusinessId(batchId, businessId)
        .orElseThrow(() -> ApiException.notFound(NOT_FOUND));
  }

  private Batch withVariant(BatchEntity batch) {
    return toDto(batch, products.summaries(List.of(batch.variantId())).get(batch.variantId()));
  }

  private Batch toDto(BatchEntity batch, VariantSummary variant) {
    Fields f = batch.fields();
    return new Batch(
        batch.id(),
        variant,
        f.warehouseId(),
        f.batchNumber(),
        f.manufacturedOn(),
        f.expiresOn(),
        f.dateType(),
        BatchRules.daysUntil(f.expiresOn(), clock),
        f.quantity(),
        batch.quantityUnit(),
        batch.packsPerCase(),
        f.mrpPaise(),
        batch.status());
  }

  private static BatchSummary toSummary(BatchEntity batch, VariantSummary variant) {
    Fields f = batch.fields();
    return new BatchSummary(
        batch.id(),
        variant,
        f.expiresOn(),
        f.dateType(),
        f.quantity(),
        batch.quantityUnit(),
        batch.packsPerCase(),
        batch.status());
  }

  private static Fields merge(Fields current, UpdateBatchRequest request) {
    return new Fields(
        request.warehouseId() == null ? current.warehouseId() : request.warehouseId(),
        request.batchNumber() == null
            ? current.batchNumber()
            : BatchRules.batchNumber(request.batchNumber()),
        request.manufacturedOn() == null ? current.manufacturedOn() : request.manufacturedOn(),
        request.expiresOn() == null ? current.expiresOn() : request.expiresOn(),
        request.dateType() == null ? current.dateType() : request.dateType(),
        request.quantity() == null ? current.quantity() : request.quantity(),
        request.mrpPaise() == null ? current.mrpPaise() : request.mrpPaise());
  }

  static List<String> changedFields(Fields before, Fields after) {
    Map<String, Function<Fields, Object>> fields = new LinkedHashMap<>();
    fields.put("warehouseId", Fields::warehouseId);
    fields.put("batchNumber", Fields::batchNumber);
    fields.put("manufacturedOn", Fields::manufacturedOn);
    fields.put("expiresOn", Fields::expiresOn);
    fields.put("dateType", Fields::dateType);
    fields.put("quantity", Fields::quantity);
    fields.put("mrpPaise", Fields::mrpPaise);
    List<String> changed = new ArrayList<>();
    fields.forEach(
        (name, get) -> {
          if (!Objects.equals(get.apply(before), get.apply(after))) {
            changed.add(name);
          }
        });
    return List.copyOf(changed);
  }

  /** Field names always; the old and new printed dates too, since they matter for review. */
  private static Map<String, Object> auditDetails(
      Fields before, Fields after, List<String> changed) {
    Map<String, Object> details = new HashMap<>();
    details.put("changedFields", changed);
    if (!Objects.equals(before.expiresOn(), after.expiresOn())) {
      details.put("expiresOnBefore", before.expiresOn().toString());
      details.put("expiresOnAfter", after.expiresOn().toString());
    }
    if (!Objects.equals(before.manufacturedOn(), after.manufacturedOn())) {
      details.put("manufacturedOnBefore", String.valueOf(before.manufacturedOn()));
      details.put("manufacturedOnAfter", String.valueOf(after.manufacturedOn()));
    }
    return details;
  }

  private static ApiException duplicateBatch() {
    return ApiException.conflict("You already have this batch number for this product.");
  }
}
