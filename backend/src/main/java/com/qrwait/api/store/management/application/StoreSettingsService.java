package com.qrwait.api.store.management.application;

import com.qrwait.api.store.management.dto.StoreSettingsResponse;
import com.qrwait.api.store.management.dto.UpdateStoreSettingsRequest;
import com.qrwait.api.store.domain.StoreNotFoundException;
import com.qrwait.api.store.domain.StoreRepository;
import com.qrwait.api.store.domain.StoreSettings;
import com.qrwait.api.store.domain.StoreSettingsRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StoreSettingsService {

  private final StoreRepository storeRepository;
  private final StoreSettingsRepository storeSettingsRepository;

  @Transactional(readOnly = true)
  public StoreSettingsResponse getSettings(UUID ownerId) {
    UUID storeId = storeRepository.getByOwnerId(ownerId).getId();
    return storeSettingsRepository.findByStoreId(storeId)
        .map(StoreSettingsResponse::from)
        .orElseThrow(() -> new StoreNotFoundException(storeId));
  }

  @Transactional
  public StoreSettingsResponse updateSettings(UUID ownerId, UpdateStoreSettingsRequest request) {
    UUID storeId = storeRepository.getByOwnerId(ownerId).getId();
    StoreSettings settings = storeSettingsRepository.findByStoreId(storeId)
        .orElseThrow(() -> new StoreNotFoundException(storeId));

    StoreSettings updated = settings.update(
        request.getTableCount(),
        request.getAvgTurnoverMinutes(),
        request.getOpenTime(),
        request.getCloseTime(),
        request.getAlertThreshold(),
        request.isAlertEnabled(),
        request.getCallGraceMinutes()
    );

    return StoreSettingsResponse.from(storeSettingsRepository.save(updated));
  }
}
