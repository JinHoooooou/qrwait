package com.qrwait.api.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class UpdateStoreInfoRequest {

  @NotBlank
  @Size(max = 100)
  private String name;

  @NotBlank
  @Size(max = 255)
  private String address;
}
