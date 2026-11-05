package com.b2bmarket.catalog.internal;

import com.b2bmarket.catalog.CategoryDtos.Category;
import com.b2bmarket.catalog.CategoryDtos.Names;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/** Read-only here: categories are seeded and changed only by migrations. */
@Entity
@Immutable
@Table(name = "categories")
public class CategoryEntity {

  @Id private UUID id;

  @Column(name = "parent_id")
  private UUID parentId;

  @Column(nullable = false)
  private String slug;

  @Column(name = "name_en", nullable = false)
  private String nameEn;

  @Column(name = "name_te", nullable = false)
  private String nameTe;

  @Column(name = "name_hi", nullable = false)
  private String nameHi;

  @Column(nullable = false)
  private boolean regulated;

  @Column(nullable = false)
  private boolean active;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  protected CategoryEntity() {}

  public UUID id() {
    return id;
  }

  public UUID parentId() {
    return parentId;
  }

  public boolean regulated() {
    return regulated;
  }

  public boolean active() {
    return active;
  }

  public Category toDto(boolean leaf) {
    return new Category(id, parentId, slug, new Names(nameEn, nameTe, nameHi), regulated, leaf);
  }
}
