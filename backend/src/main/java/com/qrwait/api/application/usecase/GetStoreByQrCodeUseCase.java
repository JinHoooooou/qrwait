package com.qrwait.api.application.usecase;

import com.qrwait.api.application.dto.StoreResponse;

public interface GetStoreByQrCodeUseCase {

    StoreResponse execute(String qrCode);
}
