package com.b2bmarket.batches;

import com.b2bmarket.ai.LabelReader;
import com.b2bmarket.ai.LabelReading;
import com.b2bmarket.batches.internal.LabelReadingRecords;
import com.b2bmarket.batches.internal.PhotoEntity;
import com.b2bmarket.batches.internal.PhotoRepository;
import com.b2bmarket.platform.ObjectStorage;
import com.b2bmarket.platform.TenantScope;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Reading label photos with the AI, after upload, in the background; and the stored readings. A
 * reading only flags: rules compare it with what the seller typed, and a reviewer decides.
 */
@Service
public class LabelReadings {

  private static final Logger log = LoggerFactory.getLogger(LabelReadings.class);

  /** Published when a label photo has been saved (handled after the upload commits). */
  public record LabelPhotoSaved(UUID businessId, UUID batchId, UUID photoId) {}

  /** A reading as others see it, with when it was made. */
  public record Reading(Instant readAt, UUID photoId, LabelReading reading) {}

  private final PhotoRepository photos;
  private final LabelReadingRecords records;
  private final ObjectStorage storage;
  private final LabelReader reader;
  private final TenantScope scope;
  private final TransactionTemplate transaction;
  private final Clock clock;

  LabelReadings(
      PhotoRepository photos,
      LabelReadingRecords records,
      ObjectStorage storage,
      LabelReader reader,
      TenantScope scope,
      PlatformTransactionManager transactions,
      Clock clock) {
    this.photos = photos;
    this.records = records;
    this.storage = storage;
    this.reader = reader;
    this.scope = scope;
    this.transaction = new TransactionTemplate(transactions);
    this.clock = clock;
  }

  /**
   * After the upload has committed, on another thread: fetch the photo, ask the AI (with no
   * database transaction open while waiting), store the reading. Failures are logged; the seller
   * and reviewer simply see "not checked".
   */
  @Async
  @TransactionalEventListener
  public void onLabelPhotoSaved(LabelPhotoSaved saved) {
    try {
      Optional<PhotoEntity> photo =
          transaction.execute(
              status -> {
                scope.enter(saved.businessId());
                return photos.findByIdAndBusinessIdAndBatchId(
                    saved.photoId(), saved.businessId(), saved.batchId());
              });
      if (photo == null || photo.isEmpty()) {
        return; // Deleted in the meantime.
      }
      byte[] bytes = storage.get(photo.get().storageKey());
      LabelReading reading = reader.read(bytes, photo.get().contentType());
      transaction.executeWithoutResult(
          status -> {
            scope.enter(saved.businessId());
            records.insert(
                saved.businessId(), saved.batchId(), saved.photoId(), reading, clock.instant());
          });
    } catch (RuntimeException e) {
      log.warn("Could not read label photo {}", saved.photoId(), e);
    }
  }

  /** The newest reading for a batch. Call inside a transaction scoped to the business or review. */
  @Transactional(propagation = Propagation.MANDATORY)
  public Optional<Reading> latest(UUID businessId, UUID batchId) {
    return records
        .latest(businessId, batchId)
        .map(s -> new Reading(s.at(), s.photoId(), s.reading()));
  }
}
