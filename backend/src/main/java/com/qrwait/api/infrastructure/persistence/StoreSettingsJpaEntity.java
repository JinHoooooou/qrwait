package com.qrwait.api.infrastructure.persistence;

import com.qrwait.api.domain.model.StoreSettings;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "store_settings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StoreSettingsJpaEntity {

  @Id
  @Column(columnDefinition = "uuid")
  private UUID id;

  @Column(name = "store_id", nullable = false, unique = true, columnDefinition = "uuid")
  private UUID storeId;

  @Column(name = "table_count", nullable = false)
  private int tableCount;

  @Column(name = "avg_turnover_minutes", nullable = false)
  private int avgTurnoverMinutes;

  @Column(name = "open_time")
  private LocalTime openTime;

  @Column(name = "close_time")
  private LocalTime closeTime;

  @Column(name = "alert_threshold", nullable = false)
  private int alertThreshold;

  @Column(name = "alert_enabled", nullable = false)
  private boolean alertEnabled;

  private StoreSettingsJpaEntity(UUID id, UUID storeId, int tableCount, int avgTurnoverMinutes,
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

  public static StoreSettingsJpaEntity from(StoreSettings settings) {
    return new StoreSettingsJpaEntity(settings.getId(), settings.getStoreId(),
        settings.getTableCount(), settings.getAvgTurnoverMinutes(),
        settings.getOpenTime(), settings.getCloseTime(),
        settings.getAlertThreshold(), settings.isAlertEnabled());
  }

  public StoreSettings toDomain() {
    return StoreSettings.restore(id, storeId, tableCount, avgTurnoverMinutes, openTime, closeTime, alertThreshold, alertEnabled);
  }
}
