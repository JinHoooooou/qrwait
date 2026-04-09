package com.qrwait.api.application.usecase;

import com.qrwait.api.application.dto.DailySummaryResponse;
import java.util.UUID;

public interface GetDailySummaryUseCase {

  DailySummaryResponse execute(UUID storeId);
}
