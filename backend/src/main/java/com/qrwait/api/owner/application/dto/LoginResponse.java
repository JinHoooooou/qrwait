package com.qrwait.api.owner.application.dto;

import java.util.UUID;

public record LoginResponse(
    String accessToken,
    String refreshToken,
    UUID ownerId,
    UUID storeId
) {

}
