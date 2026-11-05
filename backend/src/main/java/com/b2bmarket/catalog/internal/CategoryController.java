package com.b2bmarket.catalog.internal;

import com.b2bmarket.catalog.CategoryDtos.Category;
import com.b2bmarket.catalog.CategoryService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The category tree; any signed-in person may read it. */
@RestController
@RequestMapping("/api/v1/categories")
class CategoryController {

  private final CategoryService categories;

  CategoryController(CategoryService categories) {
    this.categories = categories;
  }

  @GetMapping
  List<Category> all() {
    return categories.all();
  }
}
