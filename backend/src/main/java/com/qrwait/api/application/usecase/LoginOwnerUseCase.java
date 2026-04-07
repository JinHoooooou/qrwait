package com.qrwait.api.application.usecase;

import com.qrwait.api.application.dto.LoginRequest;
import com.qrwait.api.application.dto.LoginResponse;

public interface LoginOwnerUseCase {

  LoginResponse execute(LoginRequest request);
}
