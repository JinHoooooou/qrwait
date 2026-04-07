package com.qrwait.api.application.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SignUpRequest {

  @Email
  @NotBlank
  private String email;

  @NotBlank
  @Size(min = 8)
  private String password;

  @NotBlank
  private String storeName;

  @NotBlank
  private String address;
}
