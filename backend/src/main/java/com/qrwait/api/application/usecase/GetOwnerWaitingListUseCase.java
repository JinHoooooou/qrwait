package com.qrwait.api.application.usecase;

import com.qrwait.api.application.dto.OwnerWaitingResponse;
import java.util.List;
import java.util.UUID;

public interface GetOwnerWaitingListUseCase {

  List<OwnerWaitingResponse> execute(UUID storeId);
}
