package com.qrwait.api.application.dto;

import com.qrwait.api.domain.model.WaitingStatus;
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
