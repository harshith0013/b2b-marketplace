package com.b2bmarket.catalog;

import com.b2bmarket.catalog.CategoryDtos.Category;
import com.b2bmarket.catalog.internal.CategoryEntity;
import com.b2bmarket.catalog.internal.CategoryRepository;
import com.b2bmarket.platform.ApiException;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The category tree. Categories are reference data, maintained through migrations for now. */
@Service
public class CategoryService {

  static final String NOT_USABLE =
      "Choose a category from the list (the most specific one, such as Bath soaps).";
  static final String REGULATED =
      "Products in this category cannot be listed yet. It needs compliance checks that are not"
          + " available in the pilot.";

  private final CategoryRepository categories;

  CategoryService(CategoryRepository categories) {
    this.categories = categories;
  }

  /** Every active category, parents before their children, each level in display order. */
  @Transactional(readOnly = true)
  public List<Category> all() {
    List<CategoryEntity> active = categories.findByActiveTrueOrderBySortOrderAscSlugAsc();
    Set<UUID> parents =
        active.stream()
            .map(CategoryEntity::parentId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    List<Category> roots =
        active.stream().filter(c -> c.parentId() == null).map(c -> c.toDto(false)).toList();
    List<Category> children =
        active.stream()
            .filter(c -> c.parentId() != null)
            .map(c -> c.toDto(!parents.contains(c.id())))
            .toList();
    return Stream.concat(roots.stream(), children.stream()).toList();
  }

  /** Whether products in this category need compliance checks (unknown ids count as regulated). */
  @Transactional(readOnly = true)
  public boolean isRegulated(UUID categoryId) {
    return categories.findById(categoryId).map(CategoryEntity::regulated).orElse(true);
  }

  /**
   * Checks that products may be filed under this category: it exists, is active, is a leaf and is
   * not regulated (regulated categories wait for the compliance engine, Phase 8). 400 otherwise.
   */
  @Transactional(readOnly = true)
  public void requireUsableForProducts(UUID categoryId) {
    CategoryEntity category =
        categories
            .findById(categoryId)
            .filter(CategoryEntity::active)
            .orElseThrow(() -> ApiException.badRequest(NOT_USABLE));
    if (categories.existsByParentIdAndActiveTrue(categoryId)) {
      throw ApiException.badRequest(NOT_USABLE);
    }
    if (category.regulated()) {
      throw ApiException.badRequest(REGULATED);
    }
  }
}
