package com.qrwait.api.application.dto;

import java.util.UUID;

public record StoreResponse(
        UUID storeId,
        String name
) {}
