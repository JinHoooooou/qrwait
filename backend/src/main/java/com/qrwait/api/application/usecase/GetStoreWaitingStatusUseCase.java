package com.qrwait.api.application.usecase;

import com.qrwait.api.waiting.application.dto.WaitingStatusResponse;
import java.util.UUID;

public interface GetStoreWaitingStatusUseCase {

    WaitingStatusResponse execute(UUID storeId);
}
