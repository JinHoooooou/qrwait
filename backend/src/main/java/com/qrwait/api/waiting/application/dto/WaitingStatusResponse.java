package com.qrwait.api.waiting.application.dto;

public record WaitingStatusResponse(
    int currentRank,
    int totalWaiting,
    int estimatedWaitMinutes
) {

}
