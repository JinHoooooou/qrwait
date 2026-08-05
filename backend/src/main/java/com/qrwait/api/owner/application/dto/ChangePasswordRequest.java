package com.qrwait.api.owner.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ChangePasswordRequest {

  @NotBlank
  private String currentPassword;

  @NotBlank
  @Size(min = 8)
  private String newPassword;
}
