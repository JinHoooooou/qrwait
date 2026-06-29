package com.qrwait.api.waiting.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class DailySummaryTest {

  @Test
  void from_상태별_카운트를_집계한다() {
    DailySummary summary = DailySummary.from(Map.of(
        WaitingStatus.WAITING, 3L,
        WaitingStatus.CALLED, 1L,
        WaitingStatus.ENTERED, 5L,
        WaitingStatus.NO_SHOW, 2L,
        WaitingStatus.CANCELLED, 1L
    ));

    assertThat(summary.getTotalRegistered()).isEqualTo(12L);
    assertThat(summary.getTotalEntered()).isEqualTo(5L);
    assertThat(summary.getTotalNoShow()).isEqualTo(2L);
    assertThat(summary.getTotalCancelled()).isEqualTo(1L);
    assertThat(summary.getCurrentWaiting()).isEqualTo(4L);
  }

  @Test
  void from_빈_맵이면_모두_0() {
    DailySummary summary = DailySummary.from(Map.of());

    assertThat(summary.getTotalRegistered()).isZero();
    assertThat(summary.getCurrentWaiting()).isZero();
    assertThat(summary.getTotalEntered()).isZero();
  }

  @Test
  void from_일부_상태만_있어도_집계() {
    DailySummary summary = DailySummary.from(Map.of(WaitingStatus.WAITING, 2L));

    assertThat(summary.getTotalRegistered()).isEqualTo(2L);
    assertThat(summary.getCurrentWaiting()).isEqualTo(2L);
    assertThat(summary.getTotalEntered()).isZero();
  }
}
