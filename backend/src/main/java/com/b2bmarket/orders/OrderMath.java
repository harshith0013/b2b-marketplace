package com.b2bmarket.orders;

import com.b2bmarket.platform.ApiException;
import com.b2bmarket.search.OfferDtos.PriceTier;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;

/** Prices for a quantity, GST and order totals. All in paise; GST rounded half up per line. */
public final class OrderMath {

  private static final BigDecimal BASIS_POINTS = BigDecimal.valueOf(10_000);

  private OrderMath() {}

  /** The unit price for this quantity: the highest tier that the quantity reaches. */
  public static long unitPrice(List<PriceTier> tiers, long quantity) {
    return tiers.stream()
        .filter(t -> t.minQuantity() <= quantity)
        .max(Comparator.comparingLong(PriceTier::minQuantity))
        .map(PriceTier::unitPricePaise)
        .orElseThrow(() -> ApiException.badRequest("Order at least the minimum order quantity."));
  }

  public record Line(
      long quantity, long unitPricePaise, int gstRateBp, long linePaise, long gstPaise) {}

  public static Line line(long quantity, long unitPricePaise, int gstRateBp) {
    long linePaise = Math.multiplyExact(quantity, unitPricePaise);
    long gst =
        BigDecimal.valueOf(linePaise)
            .multiply(BigDecimal.valueOf(gstRateBp))
            .divide(BASIS_POINTS, 0, RoundingMode.HALF_UP)
            .longValueExact();
    return new Line(quantity, unitPricePaise, gstRateBp, linePaise, gst);
  }

  public record Totals(long itemsPaise, long gstPaise, long deliveryPaise, long totalPaise) {}

  public static Totals totals(List<Line> lines, long deliveryPaise) {
    long items = lines.stream().mapToLong(Line::linePaise).sum();
    long gst = lines.stream().mapToLong(Line::gstPaise).sum();
    return new Totals(
        items, gst, deliveryPaise, Math.addExact(Math.addExact(items, gst), deliveryPaise));
  }
}
