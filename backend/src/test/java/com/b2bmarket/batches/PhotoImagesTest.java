package com.b2bmarket.batches;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.b2bmarket.platform.ApiException;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class PhotoImagesTest {

  @Test
  void removesCameraMetadataIncludingLocation() {
    byte[] upload = TestImages.jpegWithExif(TestImages.halves(), 1);
    assertThat(new String(upload, StandardCharsets.ISO_8859_1)).contains(TestImages.SECRET, "Exif");

    PhotoImages.Processed stored = PhotoImages.process(upload);

    String asText = new String(stored.bytes(), StandardCharsets.ISO_8859_1);
    assertThat(asText).doesNotContain(TestImages.SECRET).doesNotContain("Exif");
    assertThat(stored.contentType()).isEqualTo("image/jpeg");
    assertThat(stored.sha256()).hasSize(64);
  }

  @Test
  void appliesTheOrientationSoTheStoredPhotoIsUpright() throws IOException {
    byte[] upload = TestImages.jpegWithExif(TestImages.halves(), 6); // phone held sideways

    PhotoImages.Processed stored = PhotoImages.process(upload);

    assertThat(stored.width()).isEqualTo(300);
    assertThat(stored.height()).isEqualTo(400);
    BufferedImage image = ImageIO.read(new ByteArrayInputStream(stored.bytes()));
    // Turned 90° clockwise: the red left half is now on top.
    assertThat(isMostly(new Color(image.getRGB(150, 50)), Color.RED)).isTrue();
    assertThat(isMostly(new Color(image.getRGB(150, 350)), Color.BLUE)).isTrue();
  }

  @Test
  void readsOrientationFromExif() {
    assertThat(PhotoImages.exifOrientation(TestImages.jpegWithExif(TestImages.halves(), 8)))
        .isEqualTo(8);
    assertThat(PhotoImages.exifOrientation(TestImages.encode(TestImages.halves(), "jpeg")))
        .isEqualTo(1);
  }

  @Test
  void acceptsPngAndKeepsItPng() {
    PhotoImages.Processed stored =
        PhotoImages.process(TestImages.encode(TestImages.halves(), "png"));
    assertThat(stored.contentType()).isEqualTo("image/png");
    assertThat(stored.width()).isEqualTo(400);
  }

  @Test
  void judgesTheTypeByContentNotByName() {
    byte[] gif = TestImages.encode(TestImages.halves(), "gif");
    assertThatThrownBy(() -> PhotoImages.process(gif))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("JPEG or PNG");
    byte[] html = "<html><script>alert(1)</script>".getBytes(StandardCharsets.UTF_8);
    assertThatThrownBy(() -> PhotoImages.process(html)).isInstanceOf(ApiException.class);
  }

  @Test
  void refusesTinyBrokenAndOversizedFiles() {
    BufferedImage tiny = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
    assertThatThrownBy(() -> PhotoImages.process(TestImages.encode(tiny, "png")))
        .hasMessageContaining("too small");

    byte[] truncated = new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 0x01};
    assertThatThrownBy(() -> PhotoImages.process(truncated))
        .hasMessageContaining("could not be read");

    byte[] huge = new byte[PhotoImages.MAX_BYTES + 1];
    assertThatThrownBy(() -> PhotoImages.process(huge)).hasMessageContaining("5 MB");
    assertThatThrownBy(() -> PhotoImages.process(new byte[0])).isInstanceOf(ApiException.class);
  }

  static boolean isMostly(Color actual, Color expected) {
    return Math.abs(actual.getRed() - expected.getRed()) < 60
        && Math.abs(actual.getGreen() - expected.getGreen()) < 60
        && Math.abs(actual.getBlue() - expected.getBlue()) < 60;
  }
}
