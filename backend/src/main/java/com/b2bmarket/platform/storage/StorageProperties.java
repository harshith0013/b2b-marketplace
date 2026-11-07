package com.b2bmarket.platform.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Where photos are stored.
 *
 * @param endpoint blank for AWS S3; the server's URL for an S3-compatible server (SeaweedFS)
 * @param accessKey blank to use the default AWS credentials chain (an IAM role on AWS)
 * @param pathStyle true for S3-compatible servers that do not support bucket subdomains
 * @param createBucket create the bucket at startup if missing; local development and tests only
 */
@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(
    String endpoint,
    String region,
    String bucket,
    String accessKey,
    String secretKey,
    boolean pathStyle,
    boolean createBucket) {

  public StorageProperties {
    if (bucket == null || bucket.isBlank()) {
      throw new IllegalArgumentException("app.storage.bucket must be set");
    }
    if (region == null || region.isBlank()) {
      region = "ap-south-1";
    }
  }

  boolean hasEndpoint() {
    return endpoint != null && !endpoint.isBlank();
  }

  boolean hasStaticCredentials() {
    return accessKey != null && !accessKey.isBlank();
  }
}
