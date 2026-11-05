package com.b2bmarket.products.internal;

import com.b2bmarket.products.ReviewStatus;
import java.util.Collection;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/** Query pieces for product search. */
public final class ProductSpecifications {

  /** Escape character for LIKE patterns built from user input. */
  static final char ESCAPE = '\\';

  private ProductSpecifications() {}

  /** Approved products, plus the caller's own businesses' unreviewed or rejected ones. */
  public static Specification<ProductEntity> visibleTo(Collection<UUID> businessIds) {
    return (root, query, cb) -> {
      var approved = cb.equal(root.get("reviewStatus"), ReviewStatus.APPROVED);
      return businessIds.isEmpty()
          ? approved
          : cb.or(approved, root.get("createdByBusinessId").in(businessIds));
    };
  }

  public static Specification<ProductEntity> inCategory(UUID categoryId) {
    return (root, query, cb) -> cb.equal(root.get("categoryId"), categoryId);
  }

  /** Case-insensitive match anywhere in "brand name"; % and _ in the input match literally. */
  public static Specification<ProductEntity> matching(String text) {
    String pattern = "%" + escapeLike(text.toLowerCase(Locale.ROOT)) + "%";
    return (root, query, cb) ->
        cb.like(
            cb.lower(cb.concat(cb.concat(root.get("brand"), " "), root.get("name"))),
            pattern,
            ESCAPE);
  }

  static String escapeLike(String text) {
    return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }
}
