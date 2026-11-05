package com.b2bmarket.batches.internal;

import com.b2bmarket.batches.BatchDtos.Batch;
import com.b2bmarket.batches.BatchDtos.CreateBatchRequest;
import com.b2bmarket.batches.BatchDtos.UpdateBatchRequest;
import com.b2bmarket.batches.BatchService;
import com.b2bmarket.platform.PageResult;
import com.b2bmarket.users.CurrentUser;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** A business's stock batches. Membership and role checks happen in the service. */
@RestController
@RequestMapping("/api/v1/businesses/{businessId}/batches")
class BatchController {

  private final BatchService batches;

  BatchController(BatchService batches) {
    this.batches = batches;
  }

  @GetMapping
  PageResult<Batch> list(
      CurrentUser caller,
      @PathVariable UUID businessId,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size) {
    return batches.list(caller, businessId, page, size);
  }

  @PostMapping
  ResponseEntity<Batch> create(
      CurrentUser caller,
      @PathVariable UUID businessId,
      @Valid @RequestBody CreateBatchRequest request) {
    Batch created = batches.create(caller, businessId, request);
    return ResponseEntity.created(
            URI.create("/api/v1/businesses/" + businessId + "/batches/" + created.id()))
        .body(created);
  }

  @GetMapping("/{batchId}")
  Batch get(CurrentUser caller, @PathVariable UUID businessId, @PathVariable UUID batchId) {
    return batches.get(caller, businessId, batchId);
  }

  @PatchMapping("/{batchId}")
  Batch update(
      CurrentUser caller,
      @PathVariable UUID businessId,
      @PathVariable UUID batchId,
      @Valid @RequestBody UpdateBatchRequest request) {
    return batches.update(caller, businessId, batchId, request);
  }
}
