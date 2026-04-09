package com.qrwait.api.application.usecase;

import java.util.UUID;

public interface CallWaitingUseCase {

  void execute(UUID ownerId, UUID waitingId);
}
