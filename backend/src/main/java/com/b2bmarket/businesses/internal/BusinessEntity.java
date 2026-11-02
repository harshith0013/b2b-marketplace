package com.b2bmarket.businesses.internal;

import com.b2bmarket.businesses.BusinessDtos.BusinessDetails;
import com.b2bmarket.businesses.BusinessRole;
import com.b2bmarket.businesses.BusinessType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "businesses")
public class BusinessEntity {

  public static final String UNVERIFIED = "UNVERIFIED";

  @Id private UUID id;

  @Column(name = "legal_name", nullable = false)
  private String legalName;

  @Column(name = "trade_name")
  private String tradeName;

  @Enumerated(EnumType.STRING)
  @Column(name = "business_type", nullable = false)
  private BusinessType businessType;

  private String gstin;

  @Column(nullable = false)
  private String city;

  @Column(nullable = false)
  private String state;

  @Column(nullable = false)
  private String pincode;

  @Column(name = "verification_status", nullable = false)
  private String verificationStatus;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected BusinessEntity() {}

  /** Field values for creating or updating a business; already validated and normalized. */
  public record Fields(
      String legalName,
      String tradeName,
      BusinessType businessType,
      String gstin,
      String city,
      String state,
      String pincode) {}

  public static BusinessEntity create(Fields fields, Instant now) {
    BusinessEntity business = new BusinessEntity();
    business.id = UUID.randomUUID();
    business.verificationStatus = UNVERIFIED;
    business.createdAt = now;
    business.apply(fields, now);
    return business;
  }

  public void apply(Fields fields, Instant now) {
    legalName = fields.legalName();
    tradeName = fields.tradeName();
    businessType = fields.businessType();
    gstin = fields.gstin();
    city = fields.city();
    state = fields.state();
    pincode = fields.pincode();
    updatedAt = now;
  }

  public Fields fields() {
    return new Fields(legalName, tradeName, businessType, gstin, city, state, pincode);
  }

  public UUID id() {
    return id;
  }

  public String verificationStatus() {
    return verificationStatus;
  }

  /** The name shown in lists: the trade name if there is one, else the legal name. */
  public String displayName() {
    return tradeName != null ? tradeName : legalName;
  }

  public BusinessDetails toDetails(BusinessRole viewerRole) {
    return new BusinessDetails(
        id,
        legalName,
        tradeName,
        businessType,
        gstin,
        city,
        state,
        pincode,
        verificationStatus,
        viewerRole);
  }
}
