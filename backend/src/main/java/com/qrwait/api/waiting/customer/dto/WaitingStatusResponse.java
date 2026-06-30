package com.qrwait.api.waiting.customer.dto;

public record WaitingStatusResponse(
    int currentRank,
    int totalWaiting,
    int estimatedWaitMinutes
) {

}
