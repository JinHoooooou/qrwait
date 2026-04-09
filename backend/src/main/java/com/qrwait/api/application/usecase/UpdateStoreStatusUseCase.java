package com.qrwait.api.application.usecase;

import com.qrwait.api.application.dto.StoreResponse;
import com.qrwait.api.application.dto.UpdateStoreStatusRequest;
import java.util.UUID;

public interface UpdateStoreStatusUseCase {

  StoreResponse execute(UUID ownerId, UUID storeId, UpdateStoreStatusRequest request);
}
