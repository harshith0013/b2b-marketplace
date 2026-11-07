package com.b2bmarket.platform.storage;

import com.b2bmarket.platform.ObjectStorage;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;

/** {@link ObjectStorage} on S3 or an S3-compatible server. */
@Component
@EnableConfigurationProperties(StorageProperties.class)
class S3ObjectStorage implements ObjectStorage, AutoCloseable {

  private static final Logger log = LoggerFactory.getLogger(S3ObjectStorage.class);

  private final S3Client s3;
  private final StorageProperties properties;

  S3ObjectStorage(StorageProperties properties) {
    this.properties = properties;
    this.s3 = client(properties);
  }

  static S3Client client(StorageProperties properties) {
    S3ClientBuilder builder =
        S3Client.builder()
            .httpClient(UrlConnectionHttpClient.create())
            .region(Region.of(properties.region()))
            .forcePathStyle(properties.pathStyle())
            // Checksums only where S3 requires them: S3-compatible servers vary in support.
            .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
            .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED);
    if (properties.hasEndpoint()) {
      builder.endpointOverride(URI.create(properties.endpoint()));
    }
    builder.credentialsProvider(
        properties.hasStaticCredentials()
            ? StaticCredentialsProvider.create(
                AwsBasicCredentials.create(properties.accessKey(), properties.secretKey()))
            : DefaultCredentialsProvider.builder().build());
    return builder.build();
  }

  /** Local development and tests: make sure the bucket exists. Never enabled in production. */
  @EventListener(ApplicationReadyEvent.class)
  void createBucketIfAsked() {
    if (!properties.createBucket()) {
      return;
    }
    try {
      s3.headBucket(b -> b.bucket(properties.bucket()));
    } catch (NoSuchBucketException missing) {
      s3.createBucket(b -> b.bucket(properties.bucket()));
      log.info("Created storage bucket {}", properties.bucket());
    }
  }

  @Override
  public void put(String key, byte[] content, String contentType) {
    s3.putObject(
        b -> {
          b.bucket(properties.bucket()).key(key).contentType(contentType);
          if (!properties.hasEndpoint()) {
            b.serverSideEncryption(ServerSideEncryption.AES256); // Encrypted at rest on AWS.
          }
        },
        RequestBody.fromBytes(content));
  }

  @Override
  public byte[] get(String key) {
    try {
      return s3.getObjectAsBytes(b -> b.bucket(properties.bucket()).key(key)).asByteArray();
    } catch (NoSuchKeyException missing) {
      throw new IllegalStateException("Stored object is missing", missing);
    }
  }

  @Override
  public void delete(String key) {
    s3.deleteObject(b -> b.bucket(properties.bucket()).key(key));
  }

  @Override
  public void close() {
    s3.close();
  }
}
