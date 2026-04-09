package com.qrwait.api.store.application;

import com.qrwait.api.store.application.dto.StoreSettingsResponse;
import com.qrwait.api.store.application.dto.UpdateStoreSettingsRequest;
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
    UUID storeId = resolveStoreId(ownerId);
    return storeSettingsRepository.findByStoreId(storeId)
        .map(StoreSettingsResponse::from)
        .orElseThrow(() -> new StoreNotFoundException(storeId));
  }

  @Transactional
  public StoreSettingsResponse updateSettings(UUID ownerId, UpdateStoreSettingsRequest request) {
    UUID storeId = resolveStoreId(ownerId);
    StoreSettings settings = storeSettingsRepository.findByStoreId(storeId)
        .orElseThrow(() -> new StoreNotFoundException(storeId));

    StoreSettings updated = settings.update(
        request.getTableCount(),
        request.getAvgTurnoverMinutes(),
        request.getOpenTime(),
        request.getCloseTime(),
        request.getAlertThreshold(),
        request.isAlertEnabled()
    );

    return StoreSettingsResponse.from(storeSettingsRepository.save(updated));
  }

  private UUID resolveStoreId(UUID ownerId) {
    return storeRepository.findByOwnerId(ownerId)
        .orElseThrow(() -> new StoreNotFoundException("ownerId=" + ownerId))
        .getId();
  }
}
