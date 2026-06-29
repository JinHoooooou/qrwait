package com.qrwait.api.waiting.application.dto;

import com.qrwait.api.waiting.domain.DailySummary;

public record DailySummaryResponse(
    long totalRegistered,
    long totalEntered,
    long totalNoShow,
    long totalCancelled,
    long currentWaiting
) {

  public static DailySummaryResponse from(DailySummary summary) {
    return new DailySummaryResponse(
        summary.getTotalRegistered(),
        summary.getTotalEntered(),
        summary.getTotalNoShow(),
        summary.getTotalCancelled(),
        summary.getCurrentWaiting()
    );
  }
}
