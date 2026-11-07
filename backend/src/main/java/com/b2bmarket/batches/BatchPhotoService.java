package com.b2bmarket.batches;

import com.b2bmarket.auditing.AuditEntry;
import com.b2bmarket.auditing.AuditService;
import com.b2bmarket.batches.PhotoDtos.Photo;
import com.b2bmarket.batches.PhotoDtos.PhotoContent;
import com.b2bmarket.batches.internal.BatchEntity;
import com.b2bmarket.batches.internal.BatchRepository;
import com.b2bmarket.batches.internal.PhotoEntity;
import com.b2bmarket.batches.internal.PhotoRepository;
import com.b2bmarket.businesses.BusinessRole;
import com.b2bmarket.businesses.TenantAccess;
import com.b2bmarket.platform.ApiException;
import com.b2bmarket.platform.ObjectStorage;
import com.b2bmarket.users.CurrentUser;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Photos of a stock batch. Members can see them; owners and managers can add them (any time except
 * while the batch is in review) and delete them (while the batch is a draft). Reviewers read them
 * through the review queue.
 */
@Service
public class BatchPhotoService {

  private static final Logger log = LoggerFactory.getLogger(BatchPhotoService.class);

  static final String TARGET_TYPE = "batch_photo";
  static final String NOT_FOUND = "Photo not found.";
  static final int MAX_PHOTOS_PER_BATCH = 6;

  private final PhotoRepository photos;
  private final BatchRepository batches;
  private final ObjectStorage storage;
  private final TenantAccess tenants;
  private final AuditService audit;
  private final Clock clock;

  BatchPhotoService(
      PhotoRepository photos,
      BatchRepository batches,
      ObjectStorage storage,
      TenantAccess tenants,
      AuditService audit,
      Clock clock) {
    this.photos = photos;
    this.batches = batches;
    this.storage = storage;
    this.tenants = tenants;
    this.audit = audit;
    this.clock = clock;
  }

  @Transactional
  public Photo upload(
      CurrentUser caller, UUID businessId, UUID batchId, PhotoKind kind, byte[] content) {
    tenants.enter(caller, businessId, BusinessRole.OWNER_OR_MANAGER);
    BatchEntity batch = loadBatch(businessId, batchId);
    if (batch.status() == BatchStatus.PENDING_REVIEW) {
      throw ApiException.conflict(
          "This batch is being reviewed. Withdraw the listing to change its photos.");
    }
    if (photos.countByBusinessIdAndBatchId(businessId, batchId) >= MAX_PHOTOS_PER_BATCH) {
      throw ApiException.conflict(
          "A batch can have at most " + MAX_PHOTOS_PER_BATCH + " photos. Delete one first.");
    }
    PhotoImages.Processed image = PhotoImages.process(content);
    if (photos.existsByBusinessIdAndSha256AndBatchId(businessId, image.sha256(), batchId)) {
      throw ApiException.conflict("This photo is already on this batch.");
    }

    UUID photoId = UUID.randomUUID();
    String key = storageKey(businessId, batchId, photoId, image.contentType());
    storage.put(key, image.bytes(), image.contentType());
    deleteIfNotCommitted(key);
    PhotoEntity photo =
        photos.save(
            PhotoEntity.create(
                photoId,
                businessId,
                batchId,
                kind,
                new PhotoEntity.StoredImage(
                    key,
                    image.contentType(),
                    image.bytes().length,
                    image.width(),
                    image.height(),
                    image.sha256()),
                caller.userId(),
                Instant.now(clock)));
    audit.record(
        new AuditEntry(
            "batch_photo.added",
            caller.userId(),
            businessId,
            TARGET_TYPE,
            photoId,
            Map.of("batchId", batchId.toString(), "kind", kind.name())));
    return photo.toDto();
  }

  @Transactional(readOnly = true)
  public List<Photo> list(CurrentUser caller, UUID businessId, UUID batchId) {
    tenants.enter(caller, businessId, BusinessRole.ANY);
    loadBatch(businessId, batchId);
    return photosOf(businessId, batchId);
  }

  @Transactional(readOnly = true)
  public PhotoContent content(CurrentUser caller, UUID businessId, UUID batchId, UUID photoId) {
    tenants.enter(caller, businessId, BusinessRole.ANY);
    return contentOf(businessId, batchId, photoId);
  }

  @Transactional
  public void delete(CurrentUser caller, UUID businessId, UUID batchId, UUID photoId) {
    tenants.enter(caller, businessId, BusinessRole.OWNER_OR_MANAGER);
    BatchEntity batch = loadBatch(businessId, batchId);
    if (batch.status() != BatchStatus.DRAFT) {
      throw ApiException.conflict("Photos can be deleted only while the batch is a draft.");
    }
    PhotoEntity photo = load(businessId, batchId, photoId);
    photos.delete(photo);
    deleteAfterCommit(photo.storageKey());
    audit.record(
        new AuditEntry(
            "batch_photo.deleted",
            caller.userId(),
            businessId,
            TARGET_TYPE,
            photoId,
            Map.of("batchId", batchId.toString(), "kind", photo.kind().name())));
  }

  /**
   * The photos of a batch, for eligibility checks and review. Call inside a transaction already
   * scoped to the business (seller) or to review.
   */
  @Transactional(readOnly = true)
  public List<Photo> photosOf(UUID businessId, UUID batchId) {
    return photos.findByBusinessIdAndBatchIdOrderByCreatedAtAsc(businessId, batchId).stream()
        .map(PhotoEntity::toDto)
        .toList();
  }

  /** Which kinds of photo a batch has. Call inside a scoped transaction. */
  @Transactional(readOnly = true)
  public Set<PhotoKind> kindsOf(UUID businessId, UUID batchId) {
    return photosOf(businessId, batchId).stream()
        .map(Photo::kind)
        .collect(Collectors.toCollection(() -> EnumSet.noneOf(PhotoKind.class)));
  }

  /** A photo's bytes. Call inside a transaction already scoped to the business or to review. */
  @Transactional(readOnly = true)
  public PhotoContent contentOf(UUID businessId, UUID batchId, UUID photoId) {
    PhotoEntity photo = load(businessId, batchId, photoId);
    return new PhotoContent(storage.get(photo.storageKey()), photo.contentType());
  }

  private BatchEntity loadBatch(UUID businessId, UUID batchId) {
    return batches
        .findByIdAndBusinessId(batchId, businessId)
        .orElseThrow(() -> ApiException.notFound(BatchService.NOT_FOUND));
  }

  private PhotoEntity load(UUID businessId, UUID batchId, UUID photoId) {
    return photos
        .findByIdAndBusinessIdAndBatchId(photoId, businessId, batchId)
        .orElseThrow(() -> ApiException.notFound(NOT_FOUND));
  }

  static String storageKey(UUID businessId, UUID batchId, UUID photoId, String contentType) {
    String extension = PhotoImages.PNG.equals(contentType) ? "png" : "jpg";
    return "businesses/%s/batches/%s/%s.%s".formatted(businessId, batchId, photoId, extension);
  }

  /** The file is stored before the row; if the transaction fails, remove the orphaned file. */
  private void deleteIfNotCommitted(String key) {
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCompletion(int status) {
            if (status != STATUS_COMMITTED) {
              deleteQuietly(key);
            }
          }
        });
  }

  /** The row goes first; the file only once that is certain. */
  private void deleteAfterCommit(String key) {
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            deleteQuietly(key);
          }
        });
  }

  private void deleteQuietly(String key) {
    try {
      storage.delete(key);
    } catch (RuntimeException e) {
      // The row is already gone; an unreferenced private file is harmless and can be swept later.
      log.warn("Could not delete stored photo", e);
    }
  }
}
