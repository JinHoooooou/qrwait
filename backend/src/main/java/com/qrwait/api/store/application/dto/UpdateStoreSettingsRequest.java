package com.qrwait.api.store.application.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
}
