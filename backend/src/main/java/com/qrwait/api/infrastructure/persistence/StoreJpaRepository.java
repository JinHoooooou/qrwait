package com.qrwait.api.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreJpaRepository extends JpaRepository<StoreJpaEntity, UUID> {

  Optional<StoreJpaEntity> findByOwnerId(UUID ownerId);
}
