package com.qrwait.api.application.usecase;

import com.qrwait.api.application.dto.StoreSettingsResponse;
import com.qrwait.api.domain.model.StoreNotFoundException;
import com.qrwait.api.domain.repository.StoreSettingsRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GetStoreSettingsUseCaseImpl implements GetStoreSettingsUseCase {

  private final StoreSettingsRepository storeSettingsRepository;

  @Override
  @Transactional(readOnly = true)
  public StoreSettingsResponse execute(UUID storeId) {
    return storeSettingsRepository.findByStoreId(storeId)
        .map(StoreSettingsResponse::from)
        .orElseThrow(() -> new StoreNotFoundException(storeId.toString()));
  }
}
