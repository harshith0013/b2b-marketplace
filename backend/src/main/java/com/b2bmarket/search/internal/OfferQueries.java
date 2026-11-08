package com.b2bmarket.search.internal;

import com.b2bmarket.search.OfferFilters;
import com.b2bmarket.search.OfferSort;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * SQL for public offers. Every user value is a bound parameter; the only text put together here is
 * fixed fragments chosen in code (filters that apply, and the sort order). Run inside a transaction
 * scoped with {@code TenantScope.enterPublic()}, whose row policies add a second layer.
 */
@Repository
public class OfferQueries {

  /** What makes an offer visible. Repeated here although the public role's policies enforce it. */
  private static final String FROM_AND_RULES =
      """
      from listings l
      join inventory_batches b on b.id = l.batch_id
      join product_variants v on v.id = b.variant_id
      join products p on p.id = v.product_id
      join categories c on c.id = p.category_id
      left join categories pc on pc.id = c.parent_id
      join warehouses w on w.id = b.warehouse_id
      join businesses s on s.id = l.business_id
      join lateral (
        select pt.unit_price_paise from price_tiers pt
        where pt.listing_id = l.id order by pt.min_quantity limit 1
      ) t on true
      where l.status = 'PUBLISHED'
        and b.status = 'APPROVED'
        and p.review_status = 'APPROVED'
        and b.expires_on >= :minExpiry
        and b.quantity >= l.moq
      """;

  private static final String COLUMNS =
      """
      select l.id, p.brand, p.name as product_name, v.pack_size, v.pack_unit, p.category_id,
             b.date_type, b.expires_on, b.quantity, b.quantity_unit, b.packs_per_case, b.mrp_paise,
             b.batch_number, l.moq, l.gst_rate_bp, l.clearance_reason, l.visibility, l.notes,
             l.reviewed_at, w.city, w.state, w.pincode,
             s.legal_name, s.trade_name, s.verification_status, t.unit_price_paise,
             (select ph.id from batch_photos ph where ph.batch_id = b.id
              order by (ph.kind = 'PACK_FRONT') desc, ph.created_at limit 1) as cover_photo_id
      """;

  private static final String MATCH_NAME = "lower(p.brand || ' ' || p.name)";

  /** Words, typos (trigram word similarity) and category names in all three languages. */
  private static final String TEXT_MATCH =
      """
       and (p.search_vector @@ plainto_tsquery('simple', :query)
            or lower(:query) <% NAME
            or NAME like :pattern escape '\\'
            or c.name_en ilike :pattern escape '\\' or c.name_te ilike :pattern escape '\\'
            or c.name_hi ilike :pattern escape '\\'
            or pc.name_en ilike :pattern escape '\\' or pc.name_te ilike :pattern escape '\\'
            or pc.name_hi ilike :pattern escape '\\')
      """
          .replace("NAME", MATCH_NAME);

  private static final String RELEVANCE =
      ", ts_rank(p.search_vector, plainto_tsquery('simple', :query))"
          + " + word_similarity(lower(:query), "
          + MATCH_NAME
          + ") as relevance";

  private static final String PRICE_PER_PACK =
      "(t.unit_price_paise::numeric"
          + " / case when b.quantity_unit = 'CASE' then b.packs_per_case else 1 end)";

  private static final Map<OfferSort, String> ORDER =
      Map.of(
          OfferSort.RELEVANCE, " order by relevance desc, l.reviewed_at desc, l.id",
          OfferSort.EXPIRY, " order by b.expires_on, l.id",
          OfferSort.PRICE, " order by " + PRICE_PER_PACK + ", l.id",
          OfferSort.NEWEST, " order by l.reviewed_at desc nulls last, l.id");

  private final JdbcClient jdbc;

  OfferQueries(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  /** One row of an offer, as read from the database. */
  public record Row(
      UUID id,
      String brand,
      String productName,
      BigDecimal packSize,
      String packUnit,
      UUID categoryId,
      String dateType,
      LocalDate expiresOn,
      long quantity,
      String quantityUnit,
      Integer packsPerCase,
      Long mrpPaise,
      String batchNumber,
      long moq,
      int gstRateBp,
      String clearanceReason,
      boolean confidential,
      String notes,
      Instant publishedAt,
      String city,
      String state,
      String pincode,
      String sellerName,
      String verificationStatus,
      long unitPricePaise,
      UUID coverPhotoId) {}

  /** One more row than asked for, so the caller knows whether another page follows. */
  public List<Row> search(OfferFilters filters, LocalDate minExpiry) {
    Map<String, Object> params = new LinkedHashMap<>();
    params.put("minExpiry", minExpiry);
    StringBuilder sql = new StringBuilder(COLUMNS);
    if (filters.query() != null) {
      sql.append(RELEVANCE);
    }
    sql.append('\n').append(FROM_AND_RULES);
    appendFilters(sql, params, filters);
    sql.append(ORDER.get(filters.sort())).append(" limit :limit offset :offset");
    params.put("limit", filters.size() + 1);
    params.put("offset", (long) filters.page() * filters.size());
    return jdbc.sql(sql.toString()).params(params).query(OfferQueries::row).list();
  }

  public Optional<Row> find(UUID listingId, LocalDate minExpiry) {
    return jdbc.sql(COLUMNS + "\n" + FROM_AND_RULES + " and l.id = :id")
        .param("minExpiry", minExpiry)
        .param("id", listingId)
        .query(OfferQueries::row)
        .optional();
  }

  public List<long[]> priceTiers(UUID listingId) {
    return jdbc.sql(
            "select min_quantity, unit_price_paise from price_tiers where listing_id = :id"
                + " order by min_quantity")
        .param("id", listingId)
        .query((rs, i) -> new long[] {rs.getLong(1), rs.getLong(2)})
        .list();
  }

  /** A photo of a visible offer. */
  public record PhotoRow(
      UUID id, String kind, int width, int height, String storageKey, String contentType) {}

  public List<PhotoRow> photos(UUID listingId) {
    return jdbc.sql(
            """
            select ph.id, ph.kind, ph.width, ph.height, ph.storage_key, ph.content_type
            from batch_photos ph join listings l on l.batch_id = ph.batch_id
            where l.id = :id order by ph.created_at
            """)
        .param("id", listingId)
        .query(
            (rs, i) ->
                new PhotoRow(
                    rs.getObject("id", UUID.class),
                    rs.getString("kind"),
                    rs.getInt("width"),
                    rs.getInt("height"),
                    rs.getString("storage_key"),
                    rs.getString("content_type")))
        .list();
  }

  /**
   * Other offers like one: same category (or a sibling in the same parent), price per pack within
   * the given range; same category first, then same state, then closest price.
   */
  public List<Row> similar(
      UUID listingId,
      UUID categoryId,
      UUID parentId,
      String state,
      long pricePerPackPaise,
      long lowPaise,
      long highPaise,
      LocalDate minExpiry,
      int limit) {
    StringBuilder sql = new StringBuilder(COLUMNS).append('\n').append(FROM_AND_RULES);
    sql.append(" and l.id <> :id");
    sql.append(
        parentId == null
            ? " and c.id = :categoryId"
            : " and (c.id = :categoryId or c.parent_id = :parentId)");
    sql.append(" and ").append(PRICE_PER_PACK).append(" between :low and :high");
    sql.append(" order by (c.id = :categoryId) desc, (w.state = :state) desc, abs(")
        .append(PRICE_PER_PACK)
        .append(" - :pricePerPack), l.id limit :limit");
    var statement =
        jdbc.sql(sql.toString())
            .param("minExpiry", minExpiry)
            .param("id", listingId)
            .param("categoryId", categoryId)
            .param("state", state)
            .param("low", lowPaise)
            .param("high", highPaise)
            .param("pricePerPack", pricePerPackPaise)
            .param("limit", limit);
    if (parentId != null) {
      statement = statement.param("parentId", parentId);
    }
    return statement.query(OfferQueries::row).list();
  }

  public Optional<UUID> parentCategory(UUID categoryId) {
    return jdbc.sql("select parent_id from categories where id = :id")
        .param("id", categoryId)
        .query((rs, i) -> rs.getObject(1, UUID.class))
        .optional();
  }

  /**
   * What the buyer's passport summary needs about an offer's batch and its latest label reading.
   */
  public record PassportFacts(
      LocalDate expiresOn,
      Instant detailsChangedAt,
      String labelOutcome,
      Integer labelYear,
      Integer labelMonth,
      Integer labelDay) {}

  /** Call only after the offer itself was found (so it is on sale). */
  public PassportFacts passportFacts(UUID listingId) {
    return jdbc.sql(
            """
            select b.expires_on, b.details_changed_after_approval_at,
                   r.outcome, r.expiry_year, r.expiry_month, r.expiry_day
            from listings l join inventory_batches b on b.id = l.batch_id
            left join lateral (
              select lr.outcome, lr.expiry_year, lr.expiry_month, lr.expiry_day
              from label_readings lr where lr.batch_id = b.id
              order by lr.created_at desc limit 1
            ) r on true
            where l.id = :id
            """)
        .param("id", listingId)
        .query(
            (rs, i) -> {
              OffsetDateTime changed = rs.getObject(2, OffsetDateTime.class);
              return new PassportFacts(
                  rs.getObject(1, LocalDate.class),
                  changed == null ? null : changed.toInstant(),
                  rs.getString(3),
                  (Integer) rs.getObject(4),
                  (Integer) rs.getObject(5),
                  (Integer) rs.getObject(6));
            })
        .single();
  }

  /** States and their cities that currently have offers, alphabetically. */
  public List<String[]> places(LocalDate minExpiry) {
    return jdbc.sql(
            "select distinct w.state, w.city " + FROM_AND_RULES + " order by w.state, w.city")
        .param("minExpiry", minExpiry)
        .query((rs, i) -> new String[] {rs.getString(1), rs.getString(2)})
        .list();
  }

  /** Brands that currently have offers, most offers first. */
  public List<String> brands(LocalDate minExpiry, int limit) {
    return jdbc.sql(
            "select p.brand "
                + FROM_AND_RULES
                + " group by p.brand order by count(*) desc, p.brand limit :limit")
        .param("minExpiry", minExpiry)
        .param("limit", limit)
        .query((rs, i) -> rs.getString(1))
        .list();
  }

  private static void appendFilters(
      StringBuilder sql, Map<String, Object> params, OfferFilters filters) {
    if (filters.query() != null) {
      sql.append(TEXT_MATCH);
      params.put("query", filters.query());
      params.put("pattern", "%" + escapeLike(filters.query().toLowerCase(Locale.ROOT)) + "%");
    }
    if (filters.categoryId() != null) {
      sql.append(" and (c.id = :categoryId or c.parent_id = :categoryId)");
      params.put("categoryId", filters.categoryId());
    }
    if (filters.state() != null) {
      sql.append(" and lower(w.state) = lower(:state)");
      params.put("state", filters.state());
    }
    if (filters.city() != null) {
      sql.append(" and lower(w.city) = lower(:city)");
      params.put("city", filters.city());
    }
    if (filters.brand() != null) {
      sql.append(" and lower(p.brand) = lower(:brand)");
      params.put("brand", filters.brand());
    }
    if (filters.maxPricePerPackPaise() != null) {
      sql.append(" and ").append(PRICE_PER_PACK).append(" <= :maxPricePerPack");
      params.put("maxPricePerPack", filters.maxPricePerPackPaise());
    }
    if (filters.wantQuantity() != null) {
      sql.append(
          " and b.quantity_unit = :wantUnit and l.moq <= :wantQuantity"
              + " and b.quantity >= :wantQuantity");
      params.put("wantUnit", filters.wantUnit());
      params.put("wantQuantity", filters.wantQuantity());
    }
    if (filters.maxOrderValuePaise() != null) {
      sql.append(" and l.moq * t.unit_price_paise <= :maxOrderValue");
      params.put("maxOrderValue", filters.maxOrderValuePaise());
    }
  }

  static String escapeLike(String text) {
    return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }

  private static Row row(ResultSet rs, int rowNumber) throws SQLException {
    String tradeName = rs.getString("trade_name");
    OffsetDateTime reviewedAt = rs.getObject("reviewed_at", OffsetDateTime.class);
    return new Row(
        rs.getObject("id", UUID.class),
        rs.getString("brand"),
        rs.getString("product_name"),
        rs.getBigDecimal("pack_size"),
        rs.getString("pack_unit"),
        rs.getObject("category_id", UUID.class),
        rs.getString("date_type"),
        rs.getObject("expires_on", LocalDate.class),
        rs.getLong("quantity"),
        rs.getString("quantity_unit"),
        (Integer) rs.getObject("packs_per_case"),
        (Long) rs.getObject("mrp_paise"),
        rs.getString("batch_number"),
        rs.getLong("moq"),
        rs.getInt("gst_rate_bp"),
        rs.getString("clearance_reason"),
        "CONFIDENTIAL".equals(rs.getString("visibility")),
        rs.getString("notes"),
        reviewedAt == null ? null : reviewedAt.toInstant(),
        rs.getString("city"),
        rs.getString("state"),
        rs.getString("pincode"),
        tradeName != null ? tradeName : rs.getString("legal_name"),
        rs.getString("verification_status"),
        rs.getLong("unit_price_paise"),
        rs.getObject("cover_photo_id", UUID.class));
  }
}
