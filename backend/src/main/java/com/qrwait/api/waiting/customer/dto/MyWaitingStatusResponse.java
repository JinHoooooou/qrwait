package com.qrwait.api.waiting.customer.dto;

import com.qrwait.api.waiting.domain.WaitingStatus;

public record MyWaitingStatusResponse(
    int currentRank,
    int totalWaiting,
    int estimatedWaitMinutes,
    WaitingStatus status
) {

}
