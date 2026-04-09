package com.qrwait.api.store.infrastructure;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreSettingsJpaRepository extends JpaRepository<StoreSettingsJpaEntity, UUID> {

  Optional<StoreSettingsJpaEntity> findByStoreId(UUID storeId);
}
