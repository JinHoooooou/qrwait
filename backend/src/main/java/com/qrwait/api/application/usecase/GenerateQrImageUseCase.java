package com.qrwait.api.application.usecase;

import java.util.UUID;

public interface GenerateQrImageUseCase {
    byte[] execute(UUID storeId);
}
