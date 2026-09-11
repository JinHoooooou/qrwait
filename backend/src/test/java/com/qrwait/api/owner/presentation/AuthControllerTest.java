package com.qrwait.api.owner.presentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qrwait.api.owner.application.OwnerService;
import com.qrwait.api.owner.application.dto.LoginResponse;
import com.qrwait.api.owner.application.dto.SignUpResponse;
import com.qrwait.api.owner.domain.DuplicateEmailException;
import com.qrwait.api.owner.domain.InvalidCredentialsException;
import com.qrwait.api.owner.domain.SamePasswordException;
import com.qrwait.api.shared.security.JwtAuthFilter;
import com.qrwait.api.shared.security.JwtTokenProvider;
import com.qrwait.api.shared.security.SecurityConfig;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class})
@ActiveProfiles("test")
class AuthControllerTest {

  @Autowired
  MockMvc mockMvc;
  @Autowired
  ObjectMapper objectMapper;

  @MockitoBean
  JwtTokenProvider jwtTokenProvider;
  @MockitoBean
  OwnerService ownerService;

  @Test
  void 회원가입_성공_201반환() throws Exception {
    UUID ownerId = UUID.randomUUID();
    UUID storeId = UUID.randomUUID();
    given(ownerService.signUp(any()))
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
    given(ownerService.signUp(any()))
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
    given(ownerService.login(any()))
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
    given(ownerService.login(any()))
        .willThrow(new InvalidCredentialsException());

    Map<String, String> request = Map.of("email", "owner@test.com", "password", "wrongpassword");

    mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
  }

  @Test
  void 비밀번호변경_성공_204반환_refreshToken쿠키삭제() throws Exception {
    UUID ownerId = UUID.randomUUID();
    given(jwtTokenProvider.validateToken("valid-token")).willReturn(true);
    given(jwtTokenProvider.extractOwnerId("valid-token")).willReturn(ownerId);

    Map<String, String> request = Map.of("currentPassword", "oldPassword1", "newPassword", "newPassword1");

    mockMvc.perform(post("/api/auth/password")
            .header("Authorization", "Bearer valid-token")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isNoContent())
        .andExpect(cookie().maxAge(AuthController.REFRESH_TOKEN_COOKIE, 0));

    verify(ownerService).changePassword(eq(ownerId), any());
  }

  @Test
  void 비밀번호변경_미인증_401반환() throws Exception {
    Map<String, String> request = Map.of("currentPassword", "oldPassword1", "newPassword", "newPassword1");

    mockMvc.perform(post("/api/auth/password")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void 비밀번호변경_현재비번오류_401반환() throws Exception {
    UUID ownerId = UUID.randomUUID();
    given(jwtTokenProvider.validateToken("valid-token")).willReturn(true);
    given(jwtTokenProvider.extractOwnerId("valid-token")).willReturn(ownerId);
    org.mockito.BDDMockito.willThrow(new InvalidCredentialsException())
        .given(ownerService).changePassword(eq(ownerId), any());

    Map<String, String> request = Map.of("currentPassword", "wrongPassword", "newPassword", "newPassword1");

    mockMvc.perform(post("/api/auth/password")
            .header("Authorization", "Bearer valid-token")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
  }

  @Test
  void 비밀번호변경_새비번이_현재와동일_400반환() throws Exception {
    UUID ownerId = UUID.randomUUID();
    given(jwtTokenProvider.validateToken("valid-token")).willReturn(true);
    given(jwtTokenProvider.extractOwnerId("valid-token")).willReturn(ownerId);
    org.mockito.BDDMockito.willThrow(new SamePasswordException())
        .given(ownerService).changePassword(eq(ownerId), any());

    Map<String, String> request = Map.of("currentPassword", "samePassword1", "newPassword", "samePassword1");

    mockMvc.perform(post("/api/auth/password")
            .header("Authorization", "Bearer valid-token")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("SAME_PASSWORD"));
  }

  @Test
  void 비밀번호변경_새비번_8자미만_400반환() throws Exception {
    UUID ownerId = UUID.randomUUID();
    given(jwtTokenProvider.validateToken("valid-token")).willReturn(true);
    given(jwtTokenProvider.extractOwnerId("valid-token")).willReturn(ownerId);

    Map<String, String> request = Map.of("currentPassword", "oldPassword1", "newPassword", "short");

    mockMvc.perform(post("/api/auth/password")
            .header("Authorization", "Bearer valid-token")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
  }
}
