package com.qrwait.api.application.usecase;

import java.util.UUID;

public interface NoShowWaitingUseCase {

  void execute(UUID ownerId, UUID waitingId);
}
