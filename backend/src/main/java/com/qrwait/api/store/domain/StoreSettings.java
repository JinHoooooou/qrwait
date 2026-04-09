package com.qrwait.api.store.domain;

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

  private StoreSettings(UUID id, UUID storeId, int tableCount, int avgTurnoverMinutes,
      LocalTime openTime, LocalTime closeTime, int alertThreshold, boolean alertEnabled) {
    this.id = id;
    this.storeId = storeId;
    this.tableCount = tableCount;
    this.avgTurnoverMinutes = avgTurnoverMinutes;
    this.openTime = openTime;
    this.closeTime = closeTime;
    this.alertThreshold = alertThreshold;
    this.alertEnabled = alertEnabled;
  }

  public static StoreSettings createDefault(UUID storeId) {
    return new StoreSettings(UUID.randomUUID(), storeId, 5, 30, null, null, 10, true);
  }

  public static StoreSettings restore(UUID id, UUID storeId, int tableCount,
      int avgTurnoverMinutes, LocalTime openTime, LocalTime closeTime,
      int alertThreshold, boolean alertEnabled) {
    return new StoreSettings(id, storeId, tableCount, avgTurnoverMinutes,
        openTime, closeTime, alertThreshold, alertEnabled);
  }

  public StoreSettings update(int tableCount, int avgTurnoverMinutes, LocalTime openTime,
      LocalTime closeTime, int alertThreshold, boolean alertEnabled) {
    return new StoreSettings(id, storeId, tableCount, avgTurnoverMinutes,
        openTime, closeTime, alertThreshold, alertEnabled);
  }

  public int calculateEstimatedWait(int aheadCount) {
    return avgTurnoverMinutes / tableCount * aheadCount;
  }
}
