package com.qrwait.api.store.infrastructure;

import com.qrwait.api.store.domain.StoreSettings;
import com.qrwait.api.store.domain.StoreSettingsRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class StoreSettingsRepositoryImpl implements StoreSettingsRepository {

  private final StoreSettingsJpaRepository storeSettingsJpaRepository;

  @Override
  public StoreSettings save(StoreSettings settings) {
    return storeSettingsJpaRepository.save(StoreSettingsJpaEntity.from(settings)).toDomain();
  }

  @Override
  public Optional<StoreSettings> findByStoreId(UUID storeId) {
    return storeSettingsJpaRepository.findByStoreId(storeId).map(StoreSettingsJpaEntity::toDomain);
  }
}
