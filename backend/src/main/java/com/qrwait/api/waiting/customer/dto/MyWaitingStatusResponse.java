package com.qrwait.api.waiting.customer.dto;

import com.qrwait.api.waiting.domain.WaitingStatus;
import java.time.LocalDateTime;

public record MyWaitingStatusResponse(
    int currentRank,
    int totalWaiting,
    int estimatedWaitMinutes,
    WaitingStatus status,
    LocalDateTime graceDeadline
) {

}
