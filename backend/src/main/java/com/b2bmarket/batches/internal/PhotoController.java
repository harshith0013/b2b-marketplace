package com.b2bmarket.batches.internal;

import com.b2bmarket.batches.BatchPhotoService;
import com.b2bmarket.batches.PhotoDtos.Photo;
import com.b2bmarket.batches.PhotoDtos.PhotoContent;
import com.b2bmarket.batches.PhotoKind;
import com.b2bmarket.platform.ApiException;
import com.b2bmarket.users.CurrentUser;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Photos of one batch. Membership and role checks happen in the service. */
@RestController
@RequestMapping("/api/v1/businesses/{businessId}/batches/{batchId}/photos")
class PhotoController {

  private final BatchPhotoService photos;

  PhotoController(BatchPhotoService photos) {
    this.photos = photos;
  }

  @GetMapping
  List<Photo> list(CurrentUser caller, @PathVariable UUID businessId, @PathVariable UUID batchId) {
    return photos.list(caller, businessId, batchId);
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  ResponseEntity<Photo> upload(
      CurrentUser caller,
      @PathVariable UUID businessId,
      @PathVariable UUID batchId,
      @RequestParam PhotoKind kind,
      @RequestPart MultipartFile file) {
    Photo created = photos.upload(caller, businessId, batchId, kind, bytesOf(file));
    return ResponseEntity.created(
            URI.create(
                "/api/v1/businesses/%s/batches/%s/photos/%s"
                    .formatted(businessId, batchId, created.id())))
        .body(created);
  }

  /** The image itself. Spring Security's default no-store caching applies: photos are private. */
  @GetMapping("/{photoId}")
  ResponseEntity<byte[]> content(
      CurrentUser caller,
      @PathVariable UUID businessId,
      @PathVariable UUID batchId,
      @PathVariable UUID photoId) {
    return asImage(photos.content(caller, businessId, batchId, photoId));
  }

  @DeleteMapping("/{photoId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void delete(
      CurrentUser caller,
      @PathVariable UUID businessId,
      @PathVariable UUID batchId,
      @PathVariable UUID photoId) {
    photos.delete(caller, businessId, batchId, photoId);
  }

  static ResponseEntity<byte[]> asImage(PhotoContent content) {
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(content.contentType()))
        .header("Content-Disposition", "inline")
        .body(content.bytes());
  }

  private static byte[] bytesOf(MultipartFile file) {
    try {
      return file.getBytes();
    } catch (IOException e) {
      throw ApiException.badRequest("The photo could not be received. Try again.");
    }
  }
}
