package com.qrwait.api.application.dto;

import com.qrwait.api.domain.model.StoreStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public class UpdateStoreStatusRequest {

  @NotNull
  private StoreStatus status;
}
