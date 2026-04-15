package com.qrwait.api.waiting.application.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class RegisterWaitingRequest {

  @NotBlank(message = "전화번호는 필수입니다.")
  @Pattern(regexp = "^010-\\d{4}-\\d{4}$", message = "전화번호 형식은 010-XXXX-XXXX 이어야 합니다.")
  private String phoneNumber;

  @Min(value = 1, message = "인원은 최소 1명이어야 합니다.")
  @Max(value = 10, message = "인원은 최대 10명까지 가능합니다.")
  private int partySize;
}
