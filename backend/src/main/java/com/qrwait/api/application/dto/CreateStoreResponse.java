package com.qrwait.api.application.dto;

import java.util.UUID;

public record CreateStoreResponse(
        UUID storeId,
        String name,
        String qrUrl
) {}
