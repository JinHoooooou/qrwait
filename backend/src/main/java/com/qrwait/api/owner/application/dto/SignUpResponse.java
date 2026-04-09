package com.qrwait.api.owner.application.dto;

import java.util.UUID;

public record SignUpResponse(
    UUID ownerId,
    UUID storeId,
    String qrUrl
) {

}
