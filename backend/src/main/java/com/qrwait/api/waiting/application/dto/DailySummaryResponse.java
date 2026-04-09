package com.qrwait.api.waiting.application.dto;

public record DailySummaryResponse(
    long totalRegistered,
    long totalEntered,
    long totalNoShow,
    long totalCancelled,
    long currentWaiting
) {

}
