package com.qrwait.api.application.usecase;

import com.qrwait.api.application.dto.SignUpRequest;
import com.qrwait.api.application.dto.SignUpResponse;

public interface SignUpOwnerUseCase {

  SignUpResponse execute(SignUpRequest request);
}
