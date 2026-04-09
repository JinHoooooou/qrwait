package com.qrwait.api.store.infrastructure;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreJpaRepository extends JpaRepository<StoreJpaEntity, UUID> {

  Optional<StoreJpaEntity> findByOwnerId(UUID ownerId);
}
