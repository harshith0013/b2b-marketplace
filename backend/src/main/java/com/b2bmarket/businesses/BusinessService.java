package com.b2bmarket.businesses;

import com.b2bmarket.auditing.AuditEntry;
import com.b2bmarket.auditing.AuditService;
import com.b2bmarket.businesses.BusinessDtos.BusinessDetails;
import com.b2bmarket.businesses.BusinessDtos.CreateBusinessRequest;
import com.b2bmarket.businesses.BusinessDtos.Member;
import com.b2bmarket.businesses.BusinessDtos.Membership;
import com.b2bmarket.businesses.BusinessDtos.UpdateBusinessRequest;
import com.b2bmarket.businesses.internal.BusinessAccess;
import com.b2bmarket.businesses.internal.BusinessEntity;
import com.b2bmarket.businesses.internal.BusinessEntity.Fields;
import com.b2bmarket.businesses.internal.BusinessRepository;
import com.b2bmarket.businesses.internal.MembershipEntity;
import com.b2bmarket.businesses.internal.MembershipRepository;
import com.b2bmarket.platform.ApiException;
import com.b2bmarket.users.CurrentUser;
import com.b2bmarket.users.UserProfile;
import com.b2bmarket.users.UserService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Business accounts. Every method that takes a business id checks the caller's membership first.
 */
@Service
public class BusinessService {

  static final String TARGET_TYPE = "business";

  private final BusinessRepository businesses;
  private final MembershipRepository memberships;
  private final BusinessAccess access;
  private final UserService users;
  private final AuditService audit;
  private final Clock clock;

  BusinessService(
      BusinessRepository businesses,
      MembershipRepository memberships,
      BusinessAccess access,
      UserService users,
      AuditService audit,
      Clock clock) {
    this.businesses = businesses;
    this.memberships = memberships;
    this.access = access;
    this.users = users;
    this.audit = audit;
    this.clock = clock;
  }

  /** Creates a business; the caller becomes its owner. */
  @Transactional
  public BusinessDetails create(CurrentUser caller, CreateBusinessRequest request) {
    Fields fields =
        new Fields(
            required(request.legalName(), "Legal name"),
            optional(request.tradeName()),
            request.businessType(),
            gstinOrNull(request.gstin()),
            required(request.city(), "City"),
            required(request.state(), "State"),
            request.pincode());
    if (fields.gstin() != null && businesses.existsByGstin(fields.gstin())) {
      throw gstinTaken();
    }

    Instant now = Instant.now(clock);
    BusinessEntity business = businesses.save(BusinessEntity.create(fields, now));
    memberships.save(
        MembershipEntity.create(caller.userId(), business.id(), BusinessRole.OWNER, now));
    audit.record(
        new AuditEntry(
            "business.created",
            caller.userId(),
            business.id(),
            TARGET_TYPE,
            business.id(),
            Map.of(
                "businessType",
                fields.businessType().name(),
                "gstinGiven",
                fields.gstin() != null)));
    return business.toDetails(BusinessRole.OWNER);
  }

  @Transactional(readOnly = true)
  public BusinessDetails get(CurrentUser caller, UUID businessId) {
    MembershipEntity membership = access.require(businessId, caller.userId(), BusinessRole.ANY);
    return load(businessId).toDetails(membership.role());
  }

  /** Changes the given fields. Owner only. The GSTIN is locked once verification has started. */
  @Transactional
  public BusinessDetails update(
      CurrentUser caller, UUID businessId, UpdateBusinessRequest request) {
    access.require(businessId, caller.userId(), BusinessRole.OWNER_ONLY);
    BusinessEntity business = load(businessId);
    Fields before = business.fields();
    Fields after = merge(before, request);

    if (!Objects.equals(before.gstin(), after.gstin())) {
      if (!BusinessEntity.UNVERIFIED.equals(business.verificationStatus())) {
        throw ApiException.conflict("The GSTIN cannot be changed after verification has started.");
      }
      if (after.gstin() != null && businesses.existsByGstinAndIdNot(after.gstin(), businessId)) {
        throw gstinTaken();
      }
    }

    List<String> changed = changedFields(before, after);
    if (!changed.isEmpty()) {
      business.apply(after, Instant.now(clock));
      audit.record(
          new AuditEntry(
              "business.updated",
              caller.userId(),
              businessId,
              TARGET_TYPE,
              businessId,
              Map.of("changedFields", changed)));
    }
    return business.toDetails(BusinessRole.OWNER);
  }

  /** Who belongs to the business. Owners and managers only. */
  @Transactional(readOnly = true)
  public List<Member> members(CurrentUser caller, UUID businessId) {
    access.require(businessId, caller.userId(), BusinessRole.OWNER_OR_MANAGER);
    List<MembershipEntity> all = memberships.findByBusinessIdOrderByCreatedAtAsc(businessId);
    Map<UUID, UserProfile> profiles =
        users.profiles(all.stream().map(MembershipEntity::userId).toList());
    return all.stream()
        .filter(m -> profiles.containsKey(m.userId()))
        .map(
            m -> {
              UserProfile profile = profiles.get(m.userId());
              return new Member(m.userId(), profile.email(), profile.displayName(), m.role());
            })
        .toList();
  }

  /** The businesses a person belongs to, oldest membership first. */
  @Transactional(readOnly = true)
  public List<Membership> membershipsOf(UUID userId) {
    List<MembershipEntity> mine = memberships.findByUserIdOrderByCreatedAtAsc(userId);
    Map<UUID, BusinessEntity> byId =
        businesses.findAllById(mine.stream().map(MembershipEntity::businessId).toList()).stream()
            .collect(Collectors.toMap(BusinessEntity::id, Function.identity()));
    return mine.stream()
        .filter(m -> byId.containsKey(m.businessId()))
        .map(m -> new Membership(m.businessId(), byId.get(m.businessId()).displayName(), m.role()))
        .toList();
  }

  private BusinessEntity load(UUID businessId) {
    return businesses
        .findById(businessId)
        .orElseThrow(() -> ApiException.notFound("Business not found."));
  }

  private static Fields merge(Fields current, UpdateBusinessRequest request) {
    return new Fields(
        request.legalName() == null
            ? current.legalName()
            : required(request.legalName(), "Legal name"),
        request.tradeName() == null ? current.tradeName() : optional(request.tradeName()),
        request.businessType() == null ? current.businessType() : request.businessType(),
        request.gstin() == null ? current.gstin() : gstinOrNull(request.gstin()),
        request.city() == null ? current.city() : required(request.city(), "City"),
        request.state() == null ? current.state() : required(request.state(), "State"),
        request.pincode() == null ? current.pincode() : request.pincode());
  }

  static List<String> changedFields(Fields before, Fields after) {
    return Stream.of(
            changed("legalName", before.legalName(), after.legalName()),
            changed("tradeName", before.tradeName(), after.tradeName()),
            changed("businessType", before.businessType(), after.businessType()),
            changed("gstin", before.gstin(), after.gstin()),
            changed("city", before.city(), after.city()),
            changed("state", before.state(), after.state()),
            changed("pincode", before.pincode(), after.pincode()))
        .filter(Objects::nonNull)
        .toList();
  }

  private static String changed(String name, Object before, Object after) {
    return Objects.equals(before, after) ? null : name;
  }

  private static String required(String value, String label) {
    String trimmed = value == null ? "" : value.trim();
    if (trimmed.isEmpty()) {
      throw ApiException.badRequest(label + " is required.");
    }
    return trimmed;
  }

  /** Blank means "none". */
  private static String optional(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim();
  }

  /** Blank means "no GSTIN"; anything else must be a well-formed GSTIN. */
  private static String gstinOrNull(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String gstin = Gstin.normalize(value);
    if (!Gstin.isValid(gstin)) {
      throw ApiException.badRequest(
          "The GSTIN is not valid. Check it against your GST certificate.");
    }
    return gstin;
  }

  private static ApiException gstinTaken() {
    return ApiException.conflict("Another business is already registered with this GSTIN.");
  }
}
