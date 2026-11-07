package com.b2bmarket.batches.internal;

import com.b2bmarket.batches.PhotoDtos.Photo;
import com.b2bmarket.batches.PhotoKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/** Photos are never edited: they are added or deleted. */
@Entity
@Immutable
@Table(name = "batch_photos")
public class PhotoEntity {

  @Id private UUID id;

  @Column(name = "business_id", nullable = false)
  private UUID businessId;

  @Column(name = "batch_id", nullable = false)
  private UUID batchId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private PhotoKind kind;

  @Column(name = "storage_key", nullable = false)
  private String storageKey;

  @Column(name = "content_type", nullable = false)
  private String contentType;

  @Column(name = "size_bytes", nullable = false)
  private int sizeBytes;

  @Column(nullable = false)
  private int width;

  @Column(nullable = false)
  private int height;

  @Column(nullable = false)
  private String sha256;

  @Column(name = "uploaded_by", nullable = false)
  private UUID uploadedBy;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected PhotoEntity() {}

  /** Facts about a stored, re-encoded image. */
  public record StoredImage(
      String storageKey, String contentType, int sizeBytes, int width, int height, String sha256) {}

  public static PhotoEntity create(
      UUID id,
      UUID businessId,
      UUID batchId,
      PhotoKind kind,
      StoredImage image,
      UUID uploadedBy,
      Instant now) {
    PhotoEntity photo = new PhotoEntity();
    photo.id = id;
    photo.businessId = businessId;
    photo.batchId = batchId;
    photo.kind = kind;
    photo.storageKey = image.storageKey();
    photo.contentType = image.contentType();
    photo.sizeBytes = image.sizeBytes();
    photo.width = image.width();
    photo.height = image.height();
    photo.sha256 = image.sha256();
    photo.uploadedBy = uploadedBy;
    photo.createdAt = now;
    return photo;
  }

  public UUID id() {
    return id;
  }

  public PhotoKind kind() {
    return kind;
  }

  public String storageKey() {
    return storageKey;
  }

  public String contentType() {
    return contentType;
  }

  public Photo toDto() {
    return new Photo(id, kind, contentType, width, height, sizeBytes, createdAt);
  }
}
