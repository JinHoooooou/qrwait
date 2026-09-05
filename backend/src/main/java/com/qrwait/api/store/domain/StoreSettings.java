package com.qrwait.api.store.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;
import lombok.Getter;

@Getter
public class StoreSettings {

  private final UUID id;
  private final UUID storeId;
  private final int tableCount;
  private final int avgTurnoverMinutes;
  private final LocalTime openTime;
  private final LocalTime closeTime;
  private final int alertThreshold;
  private final boolean alertEnabled;
  private final int callGraceMinutes;

  private StoreSettings(UUID id, UUID storeId, int tableCount, int avgTurnoverMinutes,
      LocalTime openTime, LocalTime closeTime, int alertThreshold, boolean alertEnabled,
      int callGraceMinutes) {
    this.id = id;
    this.storeId = storeId;
    this.tableCount = tableCount;
    this.avgTurnoverMinutes = avgTurnoverMinutes;
    this.openTime = openTime;
    this.closeTime = closeTime;
    this.alertThreshold = alertThreshold;
    this.alertEnabled = alertEnabled;
    this.callGraceMinutes = callGraceMinutes;
  }

  private static final int DEFAULT_TABLE_COUNT = 5;
  private static final int DEFAULT_AVG_TURNOVER_MINUTES = 30;
  private static final int DEFAULT_ALERT_THRESHOLD = 10;
  private static final boolean DEFAULT_ALERT_ENABLED = true;
  private static final LocalTime DEFAULT_OPEN_TIME = LocalTime.of(5, 0);
  private static final int DEFAULT_CALL_GRACE_MINUTES = 5;

  public static StoreSettings createDefault(UUID storeId) {
    return new StoreSettings(UUID.randomUUID(), storeId, DEFAULT_TABLE_COUNT,
        DEFAULT_AVG_TURNOVER_MINUTES, DEFAULT_OPEN_TIME, null, DEFAULT_ALERT_THRESHOLD,
        DEFAULT_ALERT_ENABLED, DEFAULT_CALL_GRACE_MINUTES);
  }

  public static StoreSettings restore(UUID id, UUID storeId, int tableCount,
      int avgTurnoverMinutes, LocalTime openTime, LocalTime closeTime,
      int alertThreshold, boolean alertEnabled, int callGraceMinutes) {
    return new StoreSettings(id, storeId, tableCount, avgTurnoverMinutes,
        openTime, closeTime, alertThreshold, alertEnabled, callGraceMinutes);
  }

  public StoreSettings update(int tableCount, int avgTurnoverMinutes, LocalTime openTime,
      LocalTime closeTime, int alertThreshold, boolean alertEnabled, int callGraceMinutes) {
    if (tableCount < 1 || tableCount > 100) {
      throw new IllegalArgumentException("테이블 수는 1~100 이어야 합니다. 입력: " + tableCount);
    }
    if (openTime == null) {
      throw new IllegalArgumentException("영업 시작 시각은 null일 수 없습니다.");
    }
    if (callGraceMinutes < 0 || callGraceMinutes > 60) {
      throw new IllegalArgumentException("호출 유예 시간은 0~60분이어야 합니다. 입력: " + callGraceMinutes);
    }
    return new StoreSettings(id, storeId, tableCount, avgTurnoverMinutes,
        openTime, closeTime, alertThreshold, alertEnabled, callGraceMinutes);
  }

  /**
   * 이 시각이 속한 영업일. 영업 시작 시각 이전이면 전날로 귀속된다.
   *
   * <p>영업 시작 시각을 경계로 쓰는 이유: 자정을 넘겨 이어지는 영업(예: 18시 오픈, 새벽 2시
   * 마감)에서 새벽에 등록한 손님을 전날 영업일로 묶어야 하기 때문이다. 마감 시각을 경계로 쓰면
   * 자정 전 영업시간 전체가 "마감 시각 이전"이 되어 정상 영업 중 등록까지 전날로 잘못 귀속된다.
   */
  public LocalDate businessDateOf(LocalDateTime at) {
    return at.toLocalTime().isBefore(openTime)
        ? at.toLocalDate().minusDays(1)
        : at.toLocalDate();
  }

  /**
   * 앞선 팀 수 기준 예상 대기시간(분). 곱셈을 먼저 해 정수 나눗셈 절사를 피한다.
   */
  public int calculateEstimatedWait(int aheadCount) {
    return Math.toIntExact(Math.round((double) avgTurnoverMinutes * aheadCount / tableCount));
  }
}
