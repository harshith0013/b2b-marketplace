package com.b2bmarket.businesses;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GstinTest {

  /** A well-formed GSTIN with a correct check character, built from the algorithm under test. */
  static String validGstin(String first14) {
    return first14 + Gstin.checkCharacter(first14);
  }

  @Test
  void acceptsAPublishedRealWorldGstin() {
    assertThat(Gstin.isValid("27AAPFU0939F1ZV")).isTrue();
  }

  @Test
  void rejectsAWrongCheckCharacter() {
    assertThat(Gstin.isValid("27AAPFU0939F1ZW")).isFalse();
  }

  @Test
  void rejectsMalformedInput() {
    assertThat(Gstin.isValid(null)).isFalse();
    assertThat(Gstin.isValid("")).isFalse();
    assertThat(Gstin.isValid("27AAPFU0939F1Z")).isFalse(); // 14 characters
    assertThat(Gstin.isValid("27AAPFU0939F1AV")).isFalse(); // 14th must be Z
    assertThat(Gstin.isValid("2AAAPFU0939F1ZV")).isFalse(); // state code must be digits
  }

  @Test
  void normalizesCaseAndSpacesBeforeValidating() {
    String normalized = Gstin.normalize("  27aapfu0939f1zv ");
    assertThat(normalized).isEqualTo("27AAPFU0939F1ZV");
    assertThat(Gstin.isValid(normalized)).isTrue();
  }

  @Test
  void helperBuildsValidGstins() {
    assertThat(Gstin.isValid(validGstin("36ABCDE1234F1Z"))).isTrue();
  }
}
