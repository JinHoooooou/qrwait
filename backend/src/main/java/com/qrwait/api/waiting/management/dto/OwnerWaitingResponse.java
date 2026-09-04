package com.qrwait.api.waiting.management.dto;

import com.qrwait.api.waiting.domain.WaitingStatus;
import java.time.LocalDateTime;
import java.util.UUID;

public record OwnerWaitingResponse(
    UUID waitingId,
    int waitingNumber,
    String phoneNumber,
    int partySize,
    WaitingStatus status,
    long elapsedMinutes,
    LocalDateTime graceDeadline
) {

}
