package com.b2bmarket.users.internal;

import com.b2bmarket.users.UserProfile;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class UserEntity {

  @Id private UUID id;

  @Column(name = "identity_subject", nullable = false, updatable = false)
  private String identitySubject;

  @Column(nullable = false)
  private String email;

  private String phone;

  @Column(name = "display_name")
  private String displayName;

  @Column(name = "preferred_locale", nullable = false)
  private String preferredLocale;

  @Column(nullable = false)
  private String status;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected UserEntity() {}

  public static UserEntity create(String identitySubject, String email, Instant now) {
    UserEntity user = new UserEntity();
    user.id = UUID.randomUUID();
    user.identitySubject = identitySubject;
    user.email = email;
    user.preferredLocale = "en";
    user.status = "ACTIVE";
    user.createdAt = now;
    user.updatedAt = now;
    return user;
  }

  public void changeEmail(String newEmail, Instant now) {
    email = newEmail;
    updatedAt = now;
  }

  public void updateProfile(String newDisplayName, String newLocale, Instant now) {
    displayName = newDisplayName;
    preferredLocale = newLocale;
    updatedAt = now;
  }

  public UUID id() {
    return id;
  }

  public String email() {
    return email;
  }

  public String displayName() {
    return displayName;
  }

  public String preferredLocale() {
    return preferredLocale;
  }

  public UserProfile toProfile() {
    return new UserProfile(id, email, displayName, preferredLocale);
  }
}
