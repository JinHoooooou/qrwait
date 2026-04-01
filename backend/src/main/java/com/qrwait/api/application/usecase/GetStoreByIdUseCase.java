package com.qrwait.api.application.usecase;

import com.qrwait.api.application.dto.StoreResponse;
import java.util.UUID;

public interface GetStoreByIdUseCase {

  StoreResponse execute(UUID storeId);
}
