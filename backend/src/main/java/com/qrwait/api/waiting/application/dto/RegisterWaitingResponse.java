package com.qrwait.api.waiting.application.dto;

import java.util.UUID;

public record RegisterWaitingResponse(
    UUID waitingId,
    int waitingNumber,
    int currentRank,
    int totalWaiting,
    int estimatedWaitMinutes,
    String waitingToken
) {

}
