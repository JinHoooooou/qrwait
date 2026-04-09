package com.qrwait.api.application.dto;

import com.qrwait.api.domain.model.StoreStatus;
import java.util.UUID;

public record StoreResponse(
    UUID storeId,
    String name,
    String address,
    StoreStatus status
) {}
