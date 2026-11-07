package com.b2bmarket.users.internal;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformRoleRepository
    extends JpaRepository<PlatformRoleEntity, PlatformRoleEntity.Key> {

  List<PlatformRoleEntity> findByKeyUserId(UUID userId);
}
