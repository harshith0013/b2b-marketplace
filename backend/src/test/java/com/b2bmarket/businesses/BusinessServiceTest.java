package com.b2bmarket.businesses;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.b2bmarket.auditing.AuditEntry;
import com.b2bmarket.auditing.AuditService;
import com.b2bmarket.businesses.BusinessDtos.BusinessDetails;
import com.b2bmarket.businesses.BusinessDtos.CreateBusinessRequest;
import com.b2bmarket.businesses.BusinessDtos.UpdateBusinessRequest;
import com.b2bmarket.businesses.internal.BusinessAccess;
import com.b2bmarket.businesses.internal.BusinessEntity;
import com.b2bmarket.businesses.internal.BusinessEntity.Fields;
import com.b2bmarket.businesses.internal.BusinessRepository;
import com.b2bmarket.businesses.internal.MembershipEntity;
import com.b2bmarket.businesses.internal.MembershipRepository;
import com.b2bmarket.platform.ApiException;
import com.b2bmarket.users.CurrentUser;
import com.b2bmarket.users.UserService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

class BusinessServiceTest {

  static final Instant NOW = Instant.parse("2026-11-02T06:00:00Z");
  static final String GSTIN = GstinTest.validGstin("36ABCDE1234F1Z");

  final BusinessRepository businesses = mock(BusinessRepository.class);
  final MembershipRepository memberships = mock(MembershipRepository.class);
  final BusinessAccess access = mock(BusinessAccess.class);
  final AuditService audit = mock(AuditService.class);
  final BusinessService service =
      new BusinessService(
          businesses,
          memberships,
          access,
          mock(UserService.class),
          audit,
          Clock.fixed(NOW, ZoneOffset.UTC));
  final CurrentUser caller = new CurrentUser(UUID.randomUUID(), "asha@example.com");

  @BeforeEach
  void saveReturnsTheEntity() {
    when(businesses.save(any())).thenAnswer(returnsFirstArg());
  }

  static CreateBusinessRequest request(String gstin) {
    return new CreateBusinessRequest(
        " Sri Lakshmi Traders ",
        "",
        BusinessType.WHOLESALER,
        gstin,
        "Guntur",
        "Andhra Pradesh",
        "522001");
  }

  @Test
  void creatorBecomesOwnerAndTheCreationIsAudited() {
    BusinessDetails created = service.create(caller, request(" " + GSTIN.toLowerCase() + " "));

    assertThat(created.yourRole()).isEqualTo(BusinessRole.OWNER);
    assertThat(created.legalName()).isEqualTo("Sri Lakshmi Traders");
    assertThat(created.tradeName()).isNull();
    assertThat(created.gstin()).isEqualTo(GSTIN);
    assertThat(created.verificationStatus()).isEqualTo("UNVERIFIED");

    ArgumentCaptor<MembershipEntity> membership = ArgumentCaptor.forClass(MembershipEntity.class);
    verify(memberships).save(membership.capture());
    assertThat(membership.getValue().userId()).isEqualTo(caller.userId());
    assertThat(membership.getValue().role()).isEqualTo(BusinessRole.OWNER);

    ArgumentCaptor<AuditEntry> entry = ArgumentCaptor.forClass(AuditEntry.class);
    verify(audit).record(entry.capture());
    assertThat(entry.getValue().action()).isEqualTo("business.created");
    assertThat(entry.getValue().businessId()).isEqualTo(created.id());
  }

  @Test
  void rejectsAMistypedGstinWithoutSavingAnything() {
    assertThatThrownBy(() -> service.create(caller, request("36ABCDE1234F1ZZ")))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.status()).isEqualTo(HttpStatus.BAD_REQUEST));
    verify(businesses, never()).save(any());
  }

  @Test
  void rejectsAGstinAlreadyUsedByAnotherBusiness() {
    when(businesses.existsByGstin(GSTIN)).thenReturn(true);

    assertThatThrownBy(() -> service.create(caller, request(GSTIN)))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.status()).isEqualTo(HttpStatus.CONFLICT));
  }

  @Test
  void onlyOwnersMayUpdateAndChangedFieldsAreAudited() {
    BusinessEntity existing = BusinessEntity.create(fields(), NOW);
    when(businesses.findById(existing.id())).thenReturn(Optional.of(existing));

    BusinessDetails updated =
        service.update(
            caller,
            existing.id(),
            new UpdateBusinessRequest(null, "Lakshmi Wholesale", null, null, null, null, null));

    verify(access).require(existing.id(), caller.userId(), BusinessRole.OWNER_ONLY);
    assertThat(updated.tradeName()).isEqualTo("Lakshmi Wholesale");
    ArgumentCaptor<AuditEntry> entry = ArgumentCaptor.forClass(AuditEntry.class);
    verify(audit).record(entry.capture());
    assertThat(entry.getValue().details()).containsEntry("changedFields", List.of("tradeName"));
  }

  @Test
  void anUpdateThatChangesNothingIsNotAudited() {
    BusinessEntity existing = BusinessEntity.create(fields(), NOW);
    when(businesses.findById(existing.id())).thenReturn(Optional.of(existing));

    service.update(
        caller,
        existing.id(),
        new UpdateBusinessRequest("Sri Lakshmi Traders", null, null, null, null, null, null));

    verify(audit, never()).record(any());
  }

  @Test
  void membersListNeedsOwnerOrManager() {
    UUID business = UUID.randomUUID();
    when(access.require(eq(business), eq(caller.userId()), any()))
        .thenThrow(ApiException.forbidden("no"));

    assertThatThrownBy(() -> service.members(caller, business)).isInstanceOf(ApiException.class);
    verify(access).require(business, caller.userId(), BusinessRole.OWNER_OR_MANAGER);
  }

  @Test
  void changedFieldsNamesOnlyWhatDiffers() {
    Fields before = fields();
    Fields after =
        new Fields(
            before.legalName(),
            "New",
            before.businessType(),
            GSTIN,
            "Vijayawada",
            before.state(),
            before.pincode());

    assertThat(BusinessService.changedFields(before, after))
        .containsExactly("tradeName", "gstin", "city");
  }

  static Fields fields() {
    return new Fields(
        "Sri Lakshmi Traders",
        null,
        BusinessType.WHOLESALER,
        null,
        "Guntur",
        "Andhra Pradesh",
        "522001");
  }
}
