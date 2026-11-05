package com.b2bmarket.catalog;

import java.util.UUID;

/** Response bodies of the categories API. */
public final class CategoryDtos {

  private CategoryDtos() {}

  /** A name in each supported language. */
  public record Names(String en, String te, String hi) {}

  /**
   * One category. Products can be filed only under a {@code leaf} (a category with no children).
   */
  public record Category(
      UUID id, UUID parentId, String slug, Names name, boolean regulated, boolean leaf) {}
}
