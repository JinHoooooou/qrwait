package com.qrwait.api.application.usecase;

import java.util.UUID;

public interface LogoutOwnerUseCase {

  void execute(UUID ownerId);
}
