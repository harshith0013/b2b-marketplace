package com.b2bmarket.products;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class GtinTest {

  @ParameterizedTest
  @ValueSource(strings = {"96385074", "036000291452", "4006381333931", "10614141000415"})
  void acceptsPublishedGs1Examples(String gtin) {
    assertThat(Gtin.isValid(gtin)).isTrue();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"4006381333932", "036000291453", "1234567", "123456789", "40063813339AB", ""})
  void rejectsWrongCheckDigitsAndLengths(String gtin) {
    assertThat(Gtin.isValid(gtin)).isFalse();
  }

  @Test
  void rejectsNull() {
    assertThat(Gtin.isValid(null)).isFalse();
  }

  @Test
  void normalizeRemovesSpacesAndHyphensTypedFromThePack() {
    assertThat(Gtin.normalize(" 4 006381-333931 ")).isEqualTo("4006381333931");
  }
}
