package com.qrwait.api.waiting.domain;

import java.util.Map;
import lombok.Getter;

@Getter
public class DailySummary {

  private final long totalRegistered;
  private final long totalEntered;
  private final long totalNoShow;
  private final long totalCancelled;
  private final long currentWaiting;

  private DailySummary(long totalRegistered, long totalEntered, long totalNoShow,
      long totalCancelled, long currentWaiting) {
    this.totalRegistered = totalRegistered;
    this.totalEntered = totalEntered;
    this.totalNoShow = totalNoShow;
    this.totalCancelled = totalCancelled;
    this.currentWaiting = currentWaiting;
  }

  public static DailySummary from(Map<WaitingStatus, Long> counts) {
    long waiting = counts.getOrDefault(WaitingStatus.WAITING, 0L);
    long called = counts.getOrDefault(WaitingStatus.CALLED, 0L);
    long entered = counts.getOrDefault(WaitingStatus.ENTERED, 0L);
    long noShow = counts.getOrDefault(WaitingStatus.NO_SHOW, 0L);
    long cancelled = counts.getOrDefault(WaitingStatus.CANCELLED, 0L);

    return new DailySummary(
        waiting + called + entered + noShow + cancelled,
        entered,
        noShow,
        cancelled,
        waiting + called
    );
  }
}
