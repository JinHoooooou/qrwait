package com.qrwait.api.store.application.dto;

import com.qrwait.api.store.domain.StoreSettings;
import java.time.LocalTime;

public record StoreSettingsResponse(
    int tableCount,
    int avgTurnoverMinutes,
    LocalTime openTime,
    LocalTime closeTime,
    int alertThreshold,
    boolean alertEnabled,
    String estimatedWaitFormulaExample
) {

  public static StoreSettingsResponse from(StoreSettings settings) {
    String formula = "대기 " + settings.getTableCount() + "팀 × "
        + settings.getAvgTurnoverMinutes() + "분 / " + settings.getTableCount() + "테이블";
    return new StoreSettingsResponse(
        settings.getTableCount(),
        settings.getAvgTurnoverMinutes(),
        settings.getOpenTime(),
        settings.getCloseTime(),
        settings.getAlertThreshold(),
        settings.isAlertEnabled(),
        formula
    );
  }
}
