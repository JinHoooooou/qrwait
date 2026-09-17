package com.qrwait.api.owner.application.dto;

public record RefreshResult(
    String accessToken,
    String refreshToken,
    long refreshTokenTtlSeconds
) {

}
