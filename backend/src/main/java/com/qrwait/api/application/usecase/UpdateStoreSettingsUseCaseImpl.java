package com.qrwait.api.application.usecase;

import com.qrwait.api.application.dto.StoreSettingsResponse;
import com.qrwait.api.application.dto.UpdateStoreSettingsRequest;
import com.qrwait.api.domain.model.Store;
import com.qrwait.api.domain.model.StoreNotFoundException;
import com.qrwait.api.domain.model.StoreSettings;
import com.qrwait.api.domain.repository.StoreRepository;
import com.qrwait.api.domain.repository.StoreSettingsRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UpdateStoreSettingsUseCaseImpl implements UpdateStoreSettingsUseCase {

  private final StoreRepository storeRepository;
  private final StoreSettingsRepository storeSettingsRepository;

  @Override
  @Transactional
  public StoreSettingsResponse execute(UUID ownerId, UUID storeId, UpdateStoreSettingsRequest request) {
    Store store = storeRepository.findById(storeId)
        .orElseThrow(() -> new StoreNotFoundException(storeId.toString()));

    if (!store.getOwnerId().equals(ownerId)) {
      throw new StoreNotFoundException(storeId.toString());
    }

    StoreSettings settings = storeSettingsRepository.findByStoreId(storeId)
        .orElseThrow(() -> new StoreNotFoundException(storeId.toString()));

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
}
