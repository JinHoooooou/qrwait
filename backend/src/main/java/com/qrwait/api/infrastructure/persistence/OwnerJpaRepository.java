package com.qrwait.api.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OwnerJpaRepository extends JpaRepository<OwnerJpaEntity, UUID> {

  Optional<OwnerJpaEntity> findByEmail(String email);
}
