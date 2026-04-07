package com.qrwait.api.application.dto;

import java.util.UUID;

public record SignUpResponse(
    UUID ownerId,
    UUID storeId,
    String qrUrl
) {

}
