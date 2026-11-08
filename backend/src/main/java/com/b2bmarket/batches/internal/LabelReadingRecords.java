package com.b2bmarket.batches.internal;

import com.b2bmarket.ai.LabelReading;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Stored label readings. Runs in the caller's transaction and database scope. */
@Repository
public class LabelReadingRecords {

  private final JdbcClient jdbc;

  LabelReadingRecords(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public void insert(
      UUID businessId, UUID batchId, UUID photoId, LabelReading reading, Instant at) {
    jdbc.sql(
            """
            insert into label_readings (id, business_id, batch_id, photo_id, created_at, outcome,
                reason, printed_date, date_type, expiry_year, expiry_month, expiry_day,
                batch_number, mrp_paise, model)
            values (:id, :business, :batch, :photo, :at, :outcome, :reason, :printed, :type,
                :year, :month, :day, :batchNumber, :mrp, :model)
            """)
        .param("id", UUID.randomUUID())
        .param("business", businessId)
        .param("batch", batchId)
        .param("photo", photoId)
        .param("at", Timestamp.from(at))
        .param("outcome", reading.outcome())
        .param("reason", reading.reason())
        .param("printed", reading.printedDate())
        .param("type", reading.dateType())
        .param("year", reading.expiryYear())
        .param("month", reading.expiryMonth())
        .param("day", reading.expiryDay())
        .param("batchNumber", reading.batchNumber())
        .param("mrp", reading.mrpPaise())
        .param("model", reading.model())
        .update();
  }

  /** A stored reading with when it was made. */
  public record Stored(Instant at, UUID photoId, LabelReading reading) {}

  /** The newest reading of a batch's label photos, if any. */
  public Optional<Stored> latest(UUID businessId, UUID batchId) {
    return jdbc.sql(
            """
            select created_at, photo_id, outcome, reason, printed_date, date_type, expiry_year,
                   expiry_month, expiry_day, batch_number, mrp_paise, model
            from label_readings where business_id = :business and batch_id = :batch
            order by created_at desc limit 1
            """)
        .param("business", businessId)
        .param("batch", batchId)
        .query(
            (rs, i) ->
                new Stored(
                    rs.getTimestamp("created_at").toInstant(),
                    rs.getObject("photo_id", UUID.class),
                    new LabelReading(
                        rs.getString("outcome"),
                        rs.getString("reason"),
                        rs.getString("printed_date"),
                        rs.getString("date_type"),
                        (Integer) rs.getObject("expiry_year"),
                        (Integer) rs.getObject("expiry_month"),
                        (Integer) rs.getObject("expiry_day"),
                        rs.getString("batch_number"),
                        (Long) rs.getObject("mrp_paise"),
                        rs.getString("model"))))
        .optional();
  }
}
