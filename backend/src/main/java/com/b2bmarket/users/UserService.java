package com.b2bmarket.users;

import com.b2bmarket.auditing.AuditEntry;
import com.b2bmarket.auditing.AuditService;
import com.b2bmarket.platform.ApiException;
import com.b2bmarket.users.internal.PlatformRoleEntity;
import com.b2bmarket.users.internal.PlatformRoleRepository;
import com.b2bmarket.users.internal.UserEntity;
import com.b2bmarket.users.internal.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class UserService {

  static final Set<String> SUPPORTED_LOCALES = Set.of("en", "te", "hi");

  private static final Logger log = LoggerFactory.getLogger(UserService.class);

  private final UserRepository users;
  private final PlatformRoleRepository platformRoles;
  private final AuditService audit;
  private final Clock clock;
  private final TransactionTemplate transaction;

  /** Local development only; empty everywhere else. */
  private final Set<String> bootstrapAdminEmails;

  UserService(
      UserRepository users,
      PlatformRoleRepository platformRoles,
      AuditService audit,
      Clock clock,
      PlatformTransactionManager transactionManager,
      @Value("${app.admin.bootstrap-email:}") String bootstrapAdminEmails) {
    this.users = users;
    this.platformRoles = platformRoles;
    this.audit = audit;
    this.clock = clock;
    this.transaction = new TransactionTemplate(transactionManager);
    this.bootstrapAdminEmails = parseEmails(bootstrapAdminEmails);
  }

  /**
   * Finds the account for a verified sign-in, creating it on first sign-in. The email must already
   * be verified by the identity provider.
   */
  public CurrentUser provision(String identitySubject, String verifiedEmail) {
    String email = verifiedEmail.trim().toLowerCase(Locale.ROOT);
    try {
      return transaction.execute(status -> findOrCreate(identitySubject, email));
    } catch (DataIntegrityViolationException raced) {
      // Two first requests from the same person arrived together and the other one created the
      // account; use it. A clash on email with a different person is reported by findOrCreate.
      return transaction.execute(status -> findOrCreate(identitySubject, email));
    }
  }

  private CurrentUser findOrCreate(String identitySubject, String email) {
    Instant now = Instant.now(clock);
    UserEntity user =
        users
            .findByIdentitySubject(identitySubject)
            .orElseGet(
                () -> {
                  rejectIfEmailTaken(email);
                  return users.saveAndFlush(UserEntity.create(identitySubject, email, now));
                });
    if (!user.email().equals(email)) {
      // The person changed their email at the identity provider and verified the new one.
      rejectIfEmailTaken(email);
      user.changeEmail(email, now);
    }
    grantBootstrapAdminIfConfigured(user, now);
    return new CurrentUser(user.id(), user.email());
  }

  /** Local development convenience: makes the configured email an admin on sign-in. */
  private void grantBootstrapAdminIfConfigured(UserEntity user, Instant now) {
    if (!bootstrapAdminEmails.contains(user.email())) {
      return;
    }
    PlatformRoleEntity.Key key = new PlatformRoleEntity.Key(user.id(), PlatformRole.ADMIN);
    if (platformRoles.existsById(key)) {
      return;
    }
    platformRoles.save(PlatformRoleEntity.grant(user.id(), PlatformRole.ADMIN, null, now));
    audit.record(
        new AuditEntry(
            "platform_role.granted",
            null,
            null,
            "user",
            user.id(),
            Map.of("role", PlatformRole.ADMIN.name(), "source", "bootstrap-email")));
    log.warn("Granted ADMIN to the bootstrap admin email. Never configure this outside local use.");
  }

  private void rejectIfEmailTaken(String email) {
    if (users.existsByEmail(email)) {
      throw ApiException.conflict("This email address is already linked to another sign-in.");
    }
  }

  @Transactional(readOnly = true)
  public UserProfile profile(UUID userId) {
    return find(userId).toProfile();
  }

  @Transactional
  public UserProfile updateProfile(UUID userId, String displayName, String preferredLocale) {
    UserEntity user = find(userId);
    String locale = preferredLocale == null ? user.preferredLocale() : preferredLocale;
    if (!SUPPORTED_LOCALES.contains(locale)) {
      throw ApiException.badRequest("Language must be one of en, te or hi.");
    }
    String name = displayName == null ? user.displayName() : blankToNull(displayName);
    user.updateProfile(name, locale, Instant.now(clock));
    return user.toProfile();
  }

  /** Profiles by id, for showing who belongs to a business. Unknown ids are left out. */
  @Transactional(readOnly = true)
  public Map<UUID, UserProfile> profiles(Collection<UUID> userIds) {
    return users.findAllById(userIds).stream()
        .map(UserEntity::toProfile)
        .collect(Collectors.toUnmodifiableMap(UserProfile::id, Function.identity()));
  }

  /** The account signed in with this email, if there is one. */
  @Transactional(readOnly = true)
  public Optional<UUID> findIdByEmail(String email) {
    return users.findByEmail(email.trim().toLowerCase(Locale.ROOT)).map(UserEntity::id);
  }

  private UserEntity find(UUID userId) {
    return users.findById(userId).orElseThrow(() -> ApiException.notFound("User not found."));
  }

  /** "a@x.com, B@y.com" → {a@x.com, b@y.com}; blank → empty. */
  static Set<String> parseEmails(String list) {
    return Arrays.stream(list.split(","))
        .map(e -> e.trim().toLowerCase(Locale.ROOT))
        .filter(e -> !e.isEmpty())
        .collect(Collectors.toUnmodifiableSet());
  }

  private static String blankToNull(String value) {
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
