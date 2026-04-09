package com.qrwait.api.application.usecase;

import com.qrwait.api.application.dto.StoreResponse;
import com.qrwait.api.application.dto.UpdateStoreInfoRequest;
import java.util.UUID;

public interface UpdateStoreInfoUseCase {

  StoreResponse execute(UUID ownerId, UpdateStoreInfoRequest request);
}
