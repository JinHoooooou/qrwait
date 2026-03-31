package com.qrwait.api.application.usecase;

import java.util.UUID;

public interface CancelWaitingUseCase {

    void execute(UUID waitingId);
}
