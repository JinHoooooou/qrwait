package com.qrwait.api.waiting.application.dto;

import com.qrwait.api.waiting.domain.WaitingStatus;
import java.util.UUID;

public record OwnerWaitingResponse(
    UUID waitingId,
    int waitingNumber,
    String visitorName,
    int partySize,
    WaitingStatus status,
    long elapsedMinutes
) {

}
