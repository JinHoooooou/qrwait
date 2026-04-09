package com.qrwait.api.owner.infrastructure;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OwnerJpaRepository extends JpaRepository<OwnerJpaEntity, UUID> {

  Optional<OwnerJpaEntity> findByEmail(String email);
}
