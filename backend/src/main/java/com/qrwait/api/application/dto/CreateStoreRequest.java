package com.qrwait.api.application.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CreateStoreRequest {

    @NotBlank(message = "매장 이름은 필수입니다.")
    private String name;
}
