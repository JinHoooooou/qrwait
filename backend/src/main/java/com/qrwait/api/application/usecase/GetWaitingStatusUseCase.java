package com.qrwait.api.application.usecase;

import com.qrwait.api.application.dto.WaitingStatusResponse;

import java.util.UUID;

public interface GetWaitingStatusUseCase {

    WaitingStatusResponse execute(UUID waitingId);
}
