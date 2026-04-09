package com.qrwait.api.application.usecase;

import java.util.UUID;

public interface EnterWaitingUseCase {

  void execute(UUID ownerId, UUID waitingId);
}
