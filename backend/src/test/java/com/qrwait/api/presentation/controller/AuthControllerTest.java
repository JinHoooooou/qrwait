package com.qrwait.api.presentation.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qrwait.api.application.dto.LoginResponse;
import com.qrwait.api.application.dto.SignUpResponse;
import com.qrwait.api.application.usecase.LoginOwnerUseCase;
import com.qrwait.api.application.usecase.LogoutOwnerUseCase;
import com.qrwait.api.application.usecase.RefreshTokenUseCase;
import com.qrwait.api.application.usecase.SignUpOwnerUseCase;
import com.qrwait.api.domain.model.DuplicateEmailException;
import com.qrwait.api.domain.model.InvalidCredentialsException;
import com.qrwait.api.shared.security.JwtAuthFilter;
import com.qrwait.api.shared.security.JwtTokenProvider;
import com.qrwait.api.shared.security.SecurityConfig;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class})
class AuthControllerTest {

  @Autowired
  MockMvc mockMvc;
  @Autowired
  ObjectMapper objectMapper;

  @MockitoBean
  JwtTokenProvider jwtTokenProvider;
  @MockitoBean
  SignUpOwnerUseCase signUpOwnerUseCase;
  @MockitoBean
  LoginOwnerUseCase loginOwnerUseCase;
  @MockitoBean
  LogoutOwnerUseCase logoutOwnerUseCase;
  @MockitoBean
  RefreshTokenUseCase refreshTokenUseCase;

  @Test
  void 회원가입_성공_201반환() throws Exception {
    UUID ownerId = UUID.randomUUID();
    UUID storeId = UUID.randomUUID();
    given(signUpOwnerUseCase.execute(any()))
        .willReturn(new SignUpResponse(ownerId, storeId, "http://localhost/wait?storeId=" + storeId));

    Map<String, String> request = Map.of(
        "email", "owner@test.com",
        "password", "password123",
        "storeName", "테스트 매장",
        "address", "서울시 강남구"
    );

    mockMvc.perform(post("/api/auth/signup")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.storeId").value(storeId.toString()))
        .andExpect(jsonPath("$.qrUrl").exists());
  }

  @Test
  void 회원가입_중복이메일_409반환() throws Exception {
    given(signUpOwnerUseCase.execute(any()))
        .willThrow(new DuplicateEmailException("owner@test.com"));

    Map<String, String> request = Map.of(
        "email", "owner@test.com",
        "password", "password123",
        "storeName", "테스트 매장",
        "address", "서울시 강남구"
    );

    mockMvc.perform(post("/api/auth/signup")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("DUPLICATE_EMAIL"));
  }

  @Test
  void 로그인_성공_200반환_쿠키설정() throws Exception {
    UUID ownerId = UUID.randomUUID();
    UUID storeId = UUID.randomUUID();
    given(loginOwnerUseCase.execute(any()))
        .willReturn(new LoginResponse("access-token", "refresh-token", ownerId, storeId));

    Map<String, String> request = Map.of("email", "owner@test.com", "password", "password123");

    mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").value("access-token"))
        .andExpect(jsonPath("$.ownerId").value(ownerId.toString()))
        .andExpect(cookie().httpOnly(AuthController.REFRESH_TOKEN_COOKIE, true));
  }

  @Test
  void 로그인_잘못된_비밀번호_401반환() throws Exception {
    given(loginOwnerUseCase.execute(any()))
        .willThrow(new InvalidCredentialsException());

    Map<String, String> request = Map.of("email", "owner@test.com", "password", "wrongpassword");

    mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
  }
}
