package com.qrwait.api.application.dto;

public record WaitingStatusResponse(
        int currentRank,
        int totalWaiting,
        int estimatedWaitMinutes
) {}
