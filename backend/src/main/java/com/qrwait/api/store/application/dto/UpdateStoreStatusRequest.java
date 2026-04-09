package com.qrwait.api.store.application.dto;

import com.qrwait.api.store.domain.StoreStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public class UpdateStoreStatusRequest {

  @NotNull
  private StoreStatus status;
}
