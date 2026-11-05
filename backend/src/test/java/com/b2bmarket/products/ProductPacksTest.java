package com.b2bmarket.products;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.b2bmarket.platform.ApiException;
import com.b2bmarket.products.ProductDtos.VariantRequest;
import com.b2bmarket.products.internal.VariantEntity;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProductPacksTest {

  @Test
  void normalizesPackSizeAndBarcode() {
    VariantEntity.Fields pack =
        ProductService.pack(
            new VariantRequest(new BigDecimal("250.000"), PackUnit.G, " 4006381-333931 "));

    assertThat(pack.packSize()).isEqualByComparingTo("250");
    assertThat(pack.packSize().scale()).isLessThanOrEqualTo(0);
    assertThat(pack.gtin()).isEqualTo("4006381333931");
  }

  @Test
  void blankBarcodeMeansNone() {
    assertThat(ProductService.pack(new VariantRequest(BigDecimal.ONE, PackUnit.L, "  ")).gtin())
        .isNull();
  }

  @Test
  void rejectsAnInvalidBarcode() {
    assertThatThrownBy(
            () ->
                ProductService.pack(
                    new VariantRequest(BigDecimal.ONE, PackUnit.L, "4006381333932")))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("barcode");
  }

  @Test
  void rejectsTheSamePackOrBarcodeTwice() {
    var a = new VariantEntity.Fields(new BigDecimal("250"), PackUnit.G, null);
    var sameSize = new VariantEntity.Fields(new BigDecimal("250"), PackUnit.G, null);
    var otherUnit = new VariantEntity.Fields(new BigDecimal("250"), PackUnit.ML, null);
    assertThatThrownBy(() -> ProductService.requireDistinctPacks(List.of(a, sameSize)))
        .hasMessageContaining("same pack size");
    ProductService.requireDistinctPacks(List.of(a, otherUnit));

    var b1 = new VariantEntity.Fields(new BigDecimal("1"), PackUnit.KG, "4006381333931");
    var b2 = new VariantEntity.Fields(new BigDecimal("2"), PackUnit.KG, "4006381333931");
    assertThatThrownBy(() -> ProductService.requireDistinctPacks(List.of(b1, b2)))
        .hasMessageContaining("same barcode");
  }
}
