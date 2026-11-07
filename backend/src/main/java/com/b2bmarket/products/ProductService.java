package com.b2bmarket.products;

import com.b2bmarket.auditing.AuditEntry;
import com.b2bmarket.auditing.AuditService;
import com.b2bmarket.businesses.BusinessDtos.Membership;
import com.b2bmarket.businesses.BusinessRole;
import com.b2bmarket.businesses.BusinessService;
import com.b2bmarket.businesses.TenantAccess;
import com.b2bmarket.catalog.CategoryService;
import com.b2bmarket.platform.ApiException;
import com.b2bmarket.platform.PageResult;
import com.b2bmarket.platform.Text;
import com.b2bmarket.products.ProductDtos.CreateProductRequest;
import com.b2bmarket.products.ProductDtos.Product;
import com.b2bmarket.products.ProductDtos.Variant;
import com.b2bmarket.products.ProductDtos.VariantRequest;
import com.b2bmarket.products.ProductDtos.VariantSummary;
import com.b2bmarket.products.internal.ProductEntity;
import com.b2bmarket.products.internal.ProductRepository;
import com.b2bmarket.products.internal.ProductSpecifications;
import com.b2bmarket.products.internal.VariantEntity;
import com.b2bmarket.products.internal.VariantRepository;
import com.b2bmarket.users.CurrentUser;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The shared product catalogue. Anyone signed in can find approved products; a business also sees
 * the products it added itself while they wait for review.
 */
@Service
public class ProductService {

  static final String TARGET_TYPE = "product";
  static final String NOT_FOUND = "Product not found.";
  static final String UNKNOWN_VARIANT = "Choose a product and pack size from the catalogue.";
  static final int MAX_QUERY_LENGTH = 100;
  static final Sort SEARCH_ORDER = Sort.by("brand", "name", "id");

  private final ProductRepository products;
  private final VariantRepository variants;
  private final CategoryService categories;
  private final BusinessService businesses;
  private final TenantAccess tenants;
  private final AuditService audit;
  private final Clock clock;

  ProductService(
      ProductRepository products,
      VariantRepository variants,
      CategoryService categories,
      BusinessService businesses,
      TenantAccess tenants,
      AuditService audit,
      Clock clock) {
    this.products = products;
    this.variants = variants;
    this.categories = categories;
    this.businesses = businesses;
    this.tenants = tenants;
    this.audit = audit;
    this.clock = clock;
  }

  /** Adds a product with its packs on behalf of a business. Owners and managers only. */
  @Transactional
  public Product create(CurrentUser caller, UUID businessId, CreateProductRequest request) {
    tenants.check(caller, businessId, BusinessRole.OWNER_OR_MANAGER);
    categories.requireUsableForProducts(request.categoryId());
    List<VariantEntity.Fields> packs =
        request.variants().stream().map(ProductService::pack).toList();
    requireDistinctPacks(packs);
    List<String> gtins =
        packs.stream().map(VariantEntity.Fields::gtin).filter(Objects::nonNull).toList();
    if (!gtins.isEmpty() && variants.existsByGtinIn(gtins)) {
      throw ApiException.conflict("A product with this barcode is already in the catalogue.");
    }

    Instant now = Instant.now(clock);
    ProductEntity product =
        products.save(
            ProductEntity.create(
                request.categoryId(),
                Text.required(request.brand(), "Brand"),
                Text.required(request.name(), "Product name"),
                Text.optional(request.description()),
                businessId,
                now));
    List<VariantEntity> saved =
        variants.saveAll(
            packs.stream().map(p -> VariantEntity.create(product.id(), p, now)).toList());
    audit.record(
        new AuditEntry(
            "product.created",
            caller.userId(),
            businessId,
            TARGET_TYPE,
            product.id(),
            Map.of("categoryId", request.categoryId().toString(), "variants", saved.size())));
    return product.toDto(saved.stream().map(VariantEntity::toDto).toList());
  }

  /** Products the caller may see, optionally in one category and matching some text. */
  @Transactional(readOnly = true)
  public PageResult<Product> search(
      CurrentUser caller, UUID categoryId, String query, Integer page, Integer size) {
    Specification<ProductEntity> spec = ProductSpecifications.visibleTo(businessIdsOf(caller));
    if (categoryId != null) {
      spec = spec.and(ProductSpecifications.inCategory(categoryId));
    }
    String text = Text.optional(query);
    if (text != null) {
      if (text.length() > MAX_QUERY_LENGTH) {
        throw ApiException.badRequest("The search text is too long.");
      }
      spec = spec.and(ProductSpecifications.matching(text));
    }
    Page<ProductEntity> found =
        products.findAll(spec, PageResult.request(page, size, SEARCH_ORDER));
    Map<UUID, List<Variant>> packs =
        variantsOf(found.getContent().stream().map(ProductEntity::id).toList());
    return PageResult.of(found, p -> p.toDto(packs.getOrDefault(p.id(), List.of())));
  }

  /** One product; 404 when it does not exist or the caller may not see it. */
  @Transactional(readOnly = true)
  public Product get(CurrentUser caller, UUID productId) {
    ProductEntity product =
        products
            .findById(productId)
            .filter(p -> p.visibleTo(businessIdsOf(caller)))
            .orElseThrow(() -> ApiException.notFound(NOT_FOUND));
    return product.toDto(variantsOf(List.of(productId)).getOrDefault(productId, List.of()));
  }

  /**
   * The variant a business wants to stock, if that business may use it (approved, or added by the
   * business itself). 400 otherwise.
   */
  @Transactional(readOnly = true)
  public VariantSummary requireUsableVariant(UUID businessId, UUID variantId) {
    VariantEntity variant =
        variants.findById(variantId).orElseThrow(() -> ApiException.badRequest(UNKNOWN_VARIANT));
    ProductEntity product =
        products
            .findById(variant.productId())
            .filter(p -> p.visibleTo(Set.of(businessId)))
            .orElseThrow(() -> ApiException.badRequest(UNKNOWN_VARIANT));
    return summary(variant, product);
  }

  /** Display details for the given variants, keyed by variant id. Unknown ids are left out. */
  @Transactional(readOnly = true)
  public Map<UUID, VariantSummary> summaries(Collection<UUID> variantIds) {
    List<VariantEntity> found = variants.findAllById(new HashSet<>(variantIds));
    Map<UUID, ProductEntity> byId =
        products
            .findAllById(found.stream().map(VariantEntity::productId).collect(Collectors.toSet()))
            .stream()
            .collect(Collectors.toMap(ProductEntity::id, Function.identity()));
    return found.stream()
        .filter(v -> byId.containsKey(v.productId()))
        .collect(Collectors.toMap(VariantEntity::id, v -> summary(v, byId.get(v.productId()))));
  }

  /**
   * A reviewer approved a listing of this product: an unreviewed product becomes approved, visible
   * to everyone. Review scope only.
   */
  @Transactional
  public void approveIfUnreviewed(UUID productId) {
    products.approveIfUnreviewed(productId, Instant.now(clock));
  }

  private Set<UUID> businessIdsOf(CurrentUser caller) {
    return businesses.membershipsOf(caller.userId()).stream()
        .map(Membership::businessId)
        .collect(Collectors.toSet());
  }

  private Map<UUID, List<Variant>> variantsOf(List<UUID> productIds) {
    if (productIds.isEmpty()) {
      return Map.of();
    }
    return variants.findByProductIdInOrderByPackUnitAscPackSizeAsc(productIds).stream()
        .collect(
            Collectors.groupingBy(
                VariantEntity::productId,
                Collectors.mapping(VariantEntity::toDto, Collectors.toList())));
  }

  private static VariantSummary summary(VariantEntity variant, ProductEntity product) {
    Variant dto = variant.toDto();
    return new VariantSummary(
        variant.id(),
        product.id(),
        product.categoryId(),
        product.brand(),
        product.name(),
        dto.packSize(),
        dto.packUnit());
  }

  static VariantEntity.Fields pack(VariantRequest request) {
    String gtin = Text.optional(request.gtin());
    if (gtin != null) {
      gtin = Gtin.normalize(gtin);
      if (!Gtin.isValid(gtin)) {
        throw ApiException.badRequest(
            "The barcode number is not valid. Type all the digits printed under the barcode.");
      }
    }
    return new VariantEntity.Fields(
        request.packSize().stripTrailingZeros(), request.packUnit(), gtin);
  }

  static void requireDistinctPacks(List<VariantEntity.Fields> packs) {
    Set<String> sizes = new HashSet<>();
    Set<String> gtins = new HashSet<>();
    for (VariantEntity.Fields pack : packs) {
      if (!sizes.add(pack.packSize().toPlainString() + " " + pack.packUnit())) {
        throw ApiException.badRequest("The same pack size is listed twice.");
      }
      if (pack.gtin() != null && !gtins.add(pack.gtin())) {
        throw ApiException.badRequest("The same barcode is listed twice.");
      }
    }
  }
}
