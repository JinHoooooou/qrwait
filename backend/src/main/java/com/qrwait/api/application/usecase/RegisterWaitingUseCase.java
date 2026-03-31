package com.qrwait.api.application.usecase;

import com.qrwait.api.application.dto.RegisterWaitingRequest;
import com.qrwait.api.application.dto.RegisterWaitingResponse;

import java.util.UUID;

public interface RegisterWaitingUseCase {

    RegisterWaitingResponse execute(UUID storeId, RegisterWaitingRequest request);
}
