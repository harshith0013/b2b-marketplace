package com.b2bmarket;

import static org.assertj.core.api.Assertions.assertThat;

import com.b2bmarket.batches.TestImages;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** Batch photos end to end, with real object storage: privacy, limits and tenant isolation. */
class PhotoIT extends SellerFixtures {

  String alice;
  String aliceBusiness;
  String batchId;
  String photosPath;

  @BeforeEach
  void aBatch() {
    alice = newPerson("alice");
    aliceBusiness = createBusiness(alice, "Photo Traders");
    batchId = createBatch(alice, aliceBusiness);
    photosPath = "/api/v1/businesses/" + aliceBusiness + "/batches/" + batchId + "/photos";
  }

  @Test
  void uploadedPhotosComeBackWithoutLocationData() {
    byte[] fromPhone = TestImages.jpegWithExif(TestImages.halves(), 6);

    JsonNode photo =
        created(upload(photosPath, alice, Map.of("kind", "LABEL_DATE"), fromPhone, "image/jpeg"));

    assertThat(photo.get("kind").asString()).isEqualTo("LABEL_DATE");
    assertThat(photo.get("width").asInt()).isEqualTo(300); // turned upright
    assertThat(photo.toString()).doesNotContain("businesses/"); // storage key never exposed

    HttpResponse<byte[]> image = getBytes(photosPath + "/" + photo.get("id").asString(), alice);
    assertThat(image.statusCode()).isEqualTo(200);
    assertThat(image.headers().firstValue("Content-Type")).hasValue("image/jpeg");
    assertThat(image.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
    String bytes = new String(image.body(), StandardCharsets.ISO_8859_1);
    assertThat(bytes).doesNotContain(TestImages.SECRET).doesNotContain("Exif");

    JsonNode list = json(call("GET", photosPath, alice, null));
    assertThat(list.size()).isEqualTo(1);
  }

  @Test
  void onlyImagesWithinTheLimitsAreAccepted() {
    byte[] notAnImage = "<svg onload=alert(1)>".getBytes(StandardCharsets.UTF_8);
    assertThat(
            upload(photosPath, alice, Map.of("kind", "OTHER"), notAnImage, "image/jpeg")
                .statusCode())
        .isEqualTo(400);
    assertThat(
            upload(
                    photosPath,
                    alice,
                    Map.of("kind", "NOPE"),
                    TestImages.encode(TestImages.halves(), "png"),
                    "image/png")
                .statusCode())
        .isEqualTo(400);

    byte[] png = TestImages.encode(TestImages.halves(), "png");
    assertThat(upload(photosPath, alice, Map.of("kind", "OTHER"), png, "image/png").statusCode())
        .isEqualTo(201);
    assertThat(upload(photosPath, alice, Map.of("kind", "OTHER"), png, "image/png").statusCode())
        .as("same photo twice")
        .isEqualTo(409);

    for (int i = 0; i < 5; i++) {
      uploadPhoto(alice, aliceBusiness, batchId, "OTHER");
    }
    byte[] seventh = TestImages.jpegWithExif(TestImages.halves(), 3);
    assertThat(
            upload(photosPath, alice, Map.of("kind", "OTHER"), seventh, "image/jpeg").statusCode())
        .isEqualTo(409);
  }

  @Test
  void anotherBusinessCannotSeeAddOrDeletePhotos() {
    String photoId = uploadPhoto(alice, aliceBusiness, batchId, "PACK_FRONT");
    String bob = newPerson("bob");
    String bobBusiness = createBusiness(bob, "Bob Photo");
    byte[] png = TestImages.encode(TestImages.halves(), "png");

    assertThat(getBytes(photosPath + "/" + photoId, bob).statusCode()).isEqualTo(404);
    assertThat(call("GET", photosPath, bob, null).statusCode()).isEqualTo(404);
    assertThat(upload(photosPath, bob, Map.of("kind", "OTHER"), png, "image/png").statusCode())
        .isEqualTo(404);
    assertThat(call("DELETE", photosPath + "/" + photoId, bob, null).statusCode()).isEqualTo(404);

    // Through his own business path, Alice's ids simply do not exist.
    String viaBob =
        "/api/v1/businesses/" + bobBusiness + "/batches/" + batchId + "/photos/" + photoId;
    assertThat(getBytes(viaBob, bob).statusCode()).isEqualTo(404);

    assertThat(getBytes(photosPath + "/" + photoId, alice).statusCode()).isEqualTo(200);
  }

  @Test
  void staffCanViewButNotUpload() {
    String photoId = uploadPhoto(alice, aliceBusiness, batchId, "PACK_FRONT");
    String staff = newPerson("staff");
    addMember(aliceBusiness, staff, "STAFF");

    assertThat(getBytes(photosPath + "/" + photoId, staff).statusCode()).isEqualTo(200);
    byte[] png = TestImages.encode(TestImages.halves(), "png");
    assertThat(upload(photosPath, staff, Map.of("kind", "OTHER"), png, "image/png").statusCode())
        .isEqualTo(403);
    assertThat(call("DELETE", photosPath + "/" + photoId, staff, null).statusCode()).isEqualTo(403);
  }

  @Test
  void draftPhotosCanBeDeletedAndAreGoneAfterwards() {
    String photoId = uploadPhoto(alice, aliceBusiness, batchId, "OTHER");

    assertThat(call("DELETE", photosPath + "/" + photoId, alice, null).statusCode()).isEqualTo(204);

    assertThat(getBytes(photosPath + "/" + photoId, alice).statusCode()).isEqualTo(404);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from audit_events where action = 'batch_photo.deleted'"
                    + " and target_id = ?::uuid",
                Integer.class,
                photoId))
        .isEqualTo(1);
  }
}
