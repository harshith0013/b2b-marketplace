package com.b2bmarket.users.internal;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {

  Optional<UserEntity> findByIdentitySubject(String identitySubject);

  boolean existsByEmail(String email);
}
