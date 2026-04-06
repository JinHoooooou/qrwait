package com.qrwait.api.domain.repository;

import com.qrwait.api.domain.model.StoreSettings;
import java.util.Optional;
import java.util.UUID;

public interface StoreSettingsRepository {

  StoreSettings save(StoreSettings settings);

  Optional<StoreSettings> findByStoreId(UUID storeId);
}
