package com.qrwait.api.store.application.dto;

import com.qrwait.api.store.domain.StoreStatus;
import java.util.UUID;

public record StoreResponse(
    UUID storeId,
    String name,
    String address,
    StoreStatus status
) {

}
