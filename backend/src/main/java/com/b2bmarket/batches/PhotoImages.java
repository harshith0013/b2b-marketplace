package com.b2bmarket.batches;

import com.b2bmarket.platform.ApiException;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Iterator;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;

/**
 * Checks an uploaded photo and re-encodes it. Re-encoding writes only the pixels, so camera
 * metadata (GPS location, device, time) never reaches storage. The EXIF orientation is applied
 * first, so photos taken with a rotated phone still look the right way up.
 */
final class PhotoImages {

  static final int MAX_BYTES = 5 * 1024 * 1024;
  static final int MIN_SIDE = 200;
  static final int MAX_SIDE = 8000;

  /** Guards against "decompression bomb" files that are small on disk but huge in memory. */
  static final long MAX_PIXELS = 40_000_000L;

  static final float JPEG_QUALITY = 0.9f;

  static final String JPEG = "image/jpeg";
  static final String PNG = "image/png";

  private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
  private static final byte[] PNG_MAGIC = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

  private PhotoImages() {}

  /** A re-encoded photo, ready to store. */
  record Processed(byte[] bytes, String contentType, int width, int height, String sha256) {}

  static Processed process(byte[] input) {
    if (input == null || input.length == 0) {
      throw ApiException.badRequest("Choose a photo to upload.");
    }
    if (input.length > MAX_BYTES) {
      throw ApiException.badRequest("The photo is larger than 5 MB.");
    }
    String contentType = detectType(input);
    BufferedImage image = decode(input, contentType);
    if (JPEG.equals(contentType)) {
      image = orient(image, exifOrientation(input));
    }
    byte[] output = JPEG.equals(contentType) ? writeJpeg(image) : writePng(image);
    return new Processed(output, contentType, image.getWidth(), image.getHeight(), sha256(output));
  }

  /** The type from the file's first bytes; the name and declared type are not trusted. */
  static String detectType(byte[] input) {
    if (startsWith(input, JPEG_MAGIC)) {
      return JPEG;
    }
    if (startsWith(input, PNG_MAGIC)) {
      return PNG;
    }
    throw ApiException.badRequest("Upload a JPEG or PNG photo.");
  }

  private static BufferedImage decode(byte[] input, String contentType) {
    try (ImageInputStream stream =
        ImageIO.createImageInputStream(new ByteArrayInputStream(input))) {
      Iterator<ImageReader> readers = ImageIO.getImageReadersByMIMEType(contentType);
      if (!readers.hasNext()) {
        throw unreadable();
      }
      ImageReader reader = readers.next();
      try {
        reader.setInput(stream, true, true);
        int width = reader.getWidth(0);
        int height = reader.getHeight(0);
        if (width < MIN_SIDE || height < MIN_SIDE) {
          throw ApiException.badRequest("The photo is too small. Take it closer to the pack.");
        }
        if (width > MAX_SIDE || height > MAX_SIDE || (long) width * height > MAX_PIXELS) {
          throw ApiException.badRequest("The photo is too large. Use your phone's normal camera.");
        }
        return reader.read(0);
      } finally {
        reader.dispose();
      }
    } catch (IOException | RuntimeException e) {
      if (e instanceof ApiException api) {
        throw api;
      }
      throw unreadable();
    }
  }

  /** EXIF orientation (1-8) of a JPEG, or 1 when there is none or it cannot be read. */
  static int exifOrientation(byte[] jpeg) {
    int i = 2;
    while (i + 4 <= jpeg.length && (jpeg[i] & 0xFF) == 0xFF) {
      int marker = jpeg[i + 1] & 0xFF;
      if (marker == 0xDA || marker == 0xD9) {
        break; // Image data starts: no more metadata segments.
      }
      int length = u16(jpeg, i + 2, false);
      if (length < 2 || i + 2 + length > jpeg.length) {
        break;
      }
      if (marker == 0xE1 && length >= 16 && isExifHeader(jpeg, i + 4)) {
        return orientationFromTiff(jpeg, i + 10, i + 2 + length);
      }
      i += 2 + length;
    }
    return 1;
  }

  private static boolean isExifHeader(byte[] b, int at) {
    return b[at] == 'E'
        && b[at + 1] == 'x'
        && b[at + 2] == 'i'
        && b[at + 3] == 'f'
        && b[at + 4] == 0
        && b[at + 5] == 0;
  }

  private static int orientationFromTiff(byte[] b, int start, int end) {
    if (start + 8 > end) {
      return 1;
    }
    boolean little = b[start] == 'I' && b[start + 1] == 'I';
    long offset = u32(b, start + 4, little);
    if (offset < 8 || start + offset + 2 > end) {
      return 1;
    }
    int ifd = start + (int) offset;
    int count = u16(b, ifd, little);
    for (int k = 0; k < count; k++) {
      int entry = ifd + 2 + k * 12;
      if (entry + 12 > end) {
        break;
      }
      if (u16(b, entry, little) == 0x0112) {
        int value = u16(b, entry + 8, little);
        return value >= 1 && value <= 8 ? value : 1;
      }
    }
    return 1;
  }

  /** Applies an EXIF orientation, so the stored image needs no orientation tag. */
  static BufferedImage orient(BufferedImage src, int orientation) {
    if (orientation == 1) {
      return src;
    }
    int w = src.getWidth();
    int h = src.getHeight();
    // AffineTransform(m00, m10, m01, m11, m02, m12): x' = m00*x + m01*y + m02, y' = m10*x + ...
    AffineTransform t =
        switch (orientation) {
          case 2 -> new AffineTransform(-1, 0, 0, 1, w, 0); // mirrored
          case 3 -> new AffineTransform(-1, 0, 0, -1, w, h); // upside down
          case 4 -> new AffineTransform(1, 0, 0, -1, 0, h); // mirrored vertically
          case 5 -> new AffineTransform(0, 1, 1, 0, 0, 0); // transposed
          case 6 -> new AffineTransform(0, 1, -1, 0, h, 0); // turn 90° clockwise
          case 7 -> new AffineTransform(0, -1, -1, 0, h, w); // transverse
          default -> new AffineTransform(0, -1, 1, 0, 0, w); // 8: turn 90° anticlockwise
        };
    boolean swapsSides = orientation >= 5;
    BufferedImage out =
        new BufferedImage(swapsSides ? h : w, swapsSides ? w : h, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = out.createGraphics();
    try {
      g.drawImage(src, t, null);
    } finally {
      g.dispose();
    }
    return out;
  }

  private static byte[] writeJpeg(BufferedImage image) {
    BufferedImage rgb = toRgb(image);
    ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ImageOutputStream out = ImageIO.createImageOutputStream(bytes)) {
      writer.setOutput(out);
      ImageWriteParam param = writer.getDefaultWriteParam();
      param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
      param.setCompressionQuality(JPEG_QUALITY);
      writer.write(null, new IIOImage(rgb, null, null), param); // No metadata carried over.
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } finally {
      writer.dispose();
    }
    return bytes.toByteArray();
  }

  private static byte[] writePng(BufferedImage image) {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try {
      ImageIO.write(image, "png", bytes);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return bytes.toByteArray();
  }

  /** JPEG has no transparency: draw onto white, in plain RGB. */
  private static BufferedImage toRgb(BufferedImage image) {
    if (image.getType() == BufferedImage.TYPE_INT_RGB) {
      return image;
    }
    BufferedImage rgb =
        new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
    Graphics2D g = rgb.createGraphics();
    try {
      g.drawImage(image, 0, 0, Color.WHITE, null);
    } finally {
      g.dispose();
    }
    return rgb;
  }

  static String sha256(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private static boolean startsWith(byte[] input, byte[] prefix) {
    if (input.length < prefix.length) {
      return false;
    }
    for (int i = 0; i < prefix.length; i++) {
      if (input[i] != prefix[i]) {
        return false;
      }
    }
    return true;
  }

  private static int u16(byte[] b, int at, boolean little) {
    int first = b[at] & 0xFF;
    int second = b[at + 1] & 0xFF;
    return little ? (second << 8) | first : (first << 8) | second;
  }

  private static long u32(byte[] b, int at, boolean little) {
    long high = u16(b, at, little);
    long low = u16(b, at + 2, little);
    return little ? (low << 16) | high : (high << 16) | low;
  }

  private static ApiException unreadable() {
    return ApiException.badRequest("This photo could not be read. Try taking it again.");
  }
}
