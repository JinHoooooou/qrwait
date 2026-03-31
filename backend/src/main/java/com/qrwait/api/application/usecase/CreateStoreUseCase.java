package com.qrwait.api.application.usecase;

import com.qrwait.api.application.dto.CreateStoreRequest;
import com.qrwait.api.application.dto.CreateStoreResponse;

public interface CreateStoreUseCase {

    CreateStoreResponse execute(CreateStoreRequest request);
}
