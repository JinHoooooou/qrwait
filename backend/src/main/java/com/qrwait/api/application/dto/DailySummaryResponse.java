package com.qrwait.api.application.dto;

public record DailySummaryResponse(
    long totalRegistered,
    long totalEntered,
    long totalNoShow,
    long totalCancelled,
    long currentWaiting
) {

}
