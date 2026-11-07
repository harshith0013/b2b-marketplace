package com.b2bmarket.platform;

/**
 * Private file storage (stock photos). Objects are never public: they are read back only through
 * the API, after an authorization check. S3 in deployed environments, SeaweedFS locally.
 */
public interface ObjectStorage {

  void put(String key, byte[] content, String contentType);

  /** The object's bytes; an {@link IllegalStateException} if it is missing. */
  byte[] get(String key);

  /** Removes the object; nothing happens if it is already gone. */
  void delete(String key);
}
