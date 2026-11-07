package com.b2bmarket.batches;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;

/** Images for photo tests: a real JPEG, optionally with a camera-style EXIF block. */
public final class TestImages {

  /** Text planted in the EXIF block; it must never survive an upload. */
  public static final String SECRET = "GPS-17.3850N-78.4867E";

  private TestImages() {}

  /** 400 x 300: left half red, right half blue. */
  public static BufferedImage halves() {
    BufferedImage image = new BufferedImage(400, 300, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = image.createGraphics();
    g.setColor(Color.RED);
    g.fillRect(0, 0, 200, 300);
    g.setColor(Color.BLUE);
    g.fillRect(200, 0, 200, 300);
    g.dispose();
    return image;
  }

  public static byte[] encode(BufferedImage image, String format) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try {
      ImageIO.write(image, format, out);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return out.toByteArray();
  }

  /**
   * A JPEG as a phone would write it: an EXIF block (big-endian TIFF) right after the start marker,
   * with the given orientation, a pointer to GPS data and the {@link #SECRET} text.
   */
  public static byte[] jpegWithExif(BufferedImage image, int orientation) {
    byte[] jpeg = encode(image, "jpeg");
    byte[] secret = (SECRET + "\0").getBytes(StandardCharsets.US_ASCII);
    // TIFF: header (8) + IFD with 2 entries (2 + 24 + 4) + the secret text.
    ByteBuffer tiff = ByteBuffer.allocate(8 + 30 + secret.length).order(ByteOrder.BIG_ENDIAN);
    tiff.put((byte) 'M').put((byte) 'M').putShort((short) 42).putInt(8);
    tiff.putShort((short) 2);
    tiff.putShort((short) 0x0112)
        .putShort((short) 3)
        .putInt(1)
        .putShort((short) orientation)
        .putShort((short) 0);
    tiff.putShort((short) 0x8825).putShort((short) 4).putInt(1).putInt(38); // GPS IFD pointer
    tiff.putInt(0);
    tiff.put(secret);
    byte[] exif = tiff.array();

    int segmentLength = 2 + 6 + exif.length;
    ByteBuffer out = ByteBuffer.allocate(jpeg.length + 2 + segmentLength);
    out.put(jpeg, 0, 2); // SOI
    out.put((byte) 0xFF).put((byte) 0xE1).putShort((short) segmentLength);
    out.put("Exif".getBytes(StandardCharsets.US_ASCII)).put((byte) 0).put((byte) 0);
    out.put(exif);
    out.put(jpeg, 2, jpeg.length - 2);
    return out.array();
  }
}
