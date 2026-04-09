package com.qrwait.api.application.usecase;

import com.qrwait.api.application.dto.StoreSettingsResponse;
import java.util.UUID;

public interface GetStoreSettingsUseCase {

  StoreSettingsResponse execute(UUID storeId);
}
