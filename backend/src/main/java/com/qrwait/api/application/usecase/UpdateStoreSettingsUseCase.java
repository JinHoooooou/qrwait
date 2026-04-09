package com.qrwait.api.application.usecase;

import com.qrwait.api.application.dto.StoreSettingsResponse;
import com.qrwait.api.application.dto.UpdateStoreSettingsRequest;
import java.util.UUID;

public interface UpdateStoreSettingsUseCase {

  StoreSettingsResponse execute(UUID ownerId, UUID storeId, UpdateStoreSettingsRequest request);
}
