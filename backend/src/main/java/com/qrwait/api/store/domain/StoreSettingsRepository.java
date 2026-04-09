package com.qrwait.api.store.domain;

import java.util.Optional;
import java.util.UUID;

public interface StoreSettingsRepository {

  StoreSettings save(StoreSettings settings);

  Optional<StoreSettings> findByStoreId(UUID storeId);
}
