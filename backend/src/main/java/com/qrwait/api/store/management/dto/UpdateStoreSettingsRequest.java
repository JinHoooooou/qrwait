package com.qrwait.api.store.management.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalTime;
import lombok.Getter;

@Getter
public class UpdateStoreSettingsRequest {

  @Min(1)
  @Max(100)
  private int tableCount;

  @Min(5)
  @Max(120)
  private int avgTurnoverMinutes;

  private LocalTime openTime;
  private LocalTime closeTime;

  @Min(1)
  @Max(100)
  private int alertThreshold;

  private boolean alertEnabled;

  @NotNull
  private LocalTime businessDayStart;

  @Min(0)
  @Max(60)
  private int callGraceMinutes;
}
