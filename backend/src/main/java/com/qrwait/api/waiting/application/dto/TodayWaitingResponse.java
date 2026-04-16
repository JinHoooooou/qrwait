package com.qrwait.api.waiting.application.dto;

import com.qrwait.api.waiting.domain.WaitingStatus;
import java.time.LocalDateTime;
import java.util.UUID;

public record TodayWaitingResponse(
    UUID waitingId,
    int waitingNumber,
    String phoneNumber,
    int partySize,
    WaitingStatus status,
    LocalDateTime createdAt
) {

}
