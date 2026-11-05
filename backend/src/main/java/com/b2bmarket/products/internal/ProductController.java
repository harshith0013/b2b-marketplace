package com.b2bmarket.products.internal;

import com.b2bmarket.platform.PageResult;
import com.b2bmarket.products.ProductDtos.CreateProductRequest;
import com.b2bmarket.products.ProductDtos.Product;
import com.b2bmarket.products.ProductService;
import com.b2bmarket.users.CurrentUser;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reading the catalogue needs only sign-in (visibility is applied in the service); adding to it
 * needs the owner or manager role in the business named in the path.
 */
@RestController
class ProductController {

  private final ProductService products;

  ProductController(ProductService products) {
    this.products = products;
  }

  @GetMapping("/api/v1/products")
  PageResult<Product> search(
      CurrentUser caller,
      @RequestParam(required = false) UUID categoryId,
      @RequestParam(required = false) String q,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size) {
    return products.search(caller, categoryId, q, page, size);
  }

  @GetMapping("/api/v1/products/{productId}")
  Product get(CurrentUser caller, @PathVariable UUID productId) {
    return products.get(caller, productId);
  }

  @PostMapping("/api/v1/businesses/{businessId}/products")
  ResponseEntity<Product> create(
      CurrentUser caller,
      @PathVariable UUID businessId,
      @Valid @RequestBody CreateProductRequest request) {
    Product created = products.create(caller, businessId, request);
    return ResponseEntity.created(URI.create("/api/v1/products/" + created.id())).body(created);
  }
}
