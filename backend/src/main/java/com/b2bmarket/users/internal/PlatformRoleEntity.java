package com.b2bmarket.users.internal;

import com.b2bmarket.users.PlatformRole;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "platform_roles")
public class PlatformRoleEntity {

  @Embeddable
  public record Key(
      @Column(name = "user_id", nullable = false) UUID userId,
      @Enumerated(EnumType.STRING) @Column(nullable = false) PlatformRole role)
      implements Serializable {}

  @EmbeddedId private Key key;

  @Column(name = "granted_by", updatable = false)
  private UUID grantedBy;

  @Column(name = "granted_at", nullable = false, updatable = false)
  private Instant grantedAt;

  protected PlatformRoleEntity() {}

  public static PlatformRoleEntity grant(
      UUID userId, PlatformRole role, UUID grantedBy, Instant now) {
    PlatformRoleEntity entity = new PlatformRoleEntity();
    entity.key = new Key(userId, role);
    entity.grantedBy = grantedBy;
    entity.grantedAt = now;
    return entity;
  }

  public PlatformRole role() {
    return key.role();
  }
}
