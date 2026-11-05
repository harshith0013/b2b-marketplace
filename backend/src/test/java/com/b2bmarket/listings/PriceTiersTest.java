package com.b2bmarket.listings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.b2bmarket.listings.ListingDtos.PriceTier;
import com.b2bmarket.listings.ListingDtos.PriceTierRequest;
import com.b2bmarket.platform.ApiException;
import java.util.List;
import org.junit.jupiter.api.Test;

class PriceTiersTest {

  static PriceTierRequest tier(long minQuantity, long paise) {
    return new PriceTierRequest(minQuantity, paise);
  }

  @Test
  void sortsTiersByQuantity() {
    List<PriceTier> tiers =
        PriceTiers.validate(10, 500, List.of(tier(100, 9_000), tier(10, 10_000), tier(50, 9_500)));

    assertThat(tiers)
        .containsExactly(
            new PriceTier(10, 10_000), new PriceTier(50, 9_500), new PriceTier(100, 9_000));
  }

  @Test
  void equalPricesForLargerQuantitiesAreAllowed() {
    assertThat(PriceTiers.validate(1, 10, List.of(tier(1, 500), tier(5, 500)))).hasSize(2);
  }

  @Test
  void firstTierMustStartAtTheMinimumOrder() {
    assertThatThrownBy(() -> PriceTiers.validate(10, 500, List.of(tier(20, 10_000))))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("minimum order quantity");
  }

  @Test
  void largerQuantitiesCannotCostMorePerUnit() {
    assertThatThrownBy(() -> PriceTiers.validate(10, 500, List.of(tier(10, 100), tier(50, 101))))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("cannot cost more");
  }

  @Test
  void quantitiesCannotRepeat() {
    assertThatThrownBy(() -> PriceTiers.validate(10, 500, List.of(tier(10, 100), tier(10, 90))))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("different quantity");
  }

  @Test
  void minimumOrderAndTiersMustFitInTheStock() {
    assertThatThrownBy(() -> PriceTiers.validate(600, 500, List.of(tier(600, 100))))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("minimum order is more than the stock");
    assertThatThrownBy(() -> PriceTiers.validate(10, 500, List.of(tier(10, 100), tier(501, 90))))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("more than the stock");
  }
}
