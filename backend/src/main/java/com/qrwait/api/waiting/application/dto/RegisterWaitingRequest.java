package com.qrwait.api.waiting.application.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class RegisterWaitingRequest {

  @NotBlank(message = "방문자 이름은 필수입니다.")
  private String visitorName;

  @Min(value = 1, message = "인원은 최소 1명이어야 합니다.")
  @Max(value = 10, message = "인원은 최대 10명까지 가능합니다.")
  private int partySize;
}
