package com.b2bmarket.listings;

import com.b2bmarket.listings.ListingDtos.PriceTier;
import com.b2bmarket.listings.ListingDtos.PriceTierRequest;
import com.b2bmarket.platform.ApiException;
import java.util.Comparator;
import java.util.List;

/** The rules for quantity-based prices on one listing. */
final class PriceTiers {

  private PriceTiers() {}

  /**
   * Sorted tiers, after checking that: the minimum order fits in the stock; the first tier starts
   * at the minimum order; quantities do not repeat or exceed the stock; and buying more never costs
   * more per unit.
   */
  static List<PriceTier> validate(long moq, long available, List<PriceTierRequest> requested) {
    if (moq > available) {
      throw ApiException.badRequest("The minimum order is more than the stock in this batch.");
    }
    List<PriceTier> tiers =
        requested.stream()
            .map(t -> new PriceTier(t.minQuantity(), t.unitPricePaise()))
            .sorted(Comparator.comparingLong(PriceTier::minQuantity))
            .toList();
    if (tiers.getFirst().minQuantity() != moq) {
      throw ApiException.badRequest("The first price must start at the minimum order quantity.");
    }
    for (int i = 1; i < tiers.size(); i++) {
      PriceTier previous = tiers.get(i - 1);
      PriceTier current = tiers.get(i);
      if (current.minQuantity() == previous.minQuantity()) {
        throw ApiException.badRequest("Each price must start at a different quantity.");
      }
      if (current.unitPricePaise() > previous.unitPricePaise()) {
        throw ApiException.badRequest("A larger quantity cannot cost more per unit.");
      }
    }
    if (tiers.getLast().minQuantity() > available) {
      throw ApiException.badRequest("A price starts at more than the stock in this batch.");
    }
    return tiers;
  }
}
