package com.qrwait.api.presentation.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JwtTokenProviderTest {

  private JwtTokenProvider jwtTokenProvider;

  @BeforeEach
  void setUp() {
    String base64Secret = Base64.getEncoder()
        .encodeToString("test-secret-key-for-unit-tests-must-be-at-least-32-bytes".getBytes());
    jwtTokenProvider = new JwtTokenProvider(base64Secret, 3600L, 604800L);
  }

  @Test
  void generateAccessToken_정상_생성() {
    UUID ownerId = UUID.randomUUID();

    String token = jwtTokenProvider.generateAccessToken(ownerId);

    assertThat(token).isNotBlank();
  }

  @Test
  void generateRefreshToken_정상_생성() {
    UUID ownerId = UUID.randomUUID();

    String token = jwtTokenProvider.generateRefreshToken(ownerId);

    assertThat(token).isNotBlank();
  }

  @Test
  void validateToken_유효한_토큰_true반환() {
    UUID ownerId = UUID.randomUUID();
    String token = jwtTokenProvider.generateAccessToken(ownerId);

    assertThat(jwtTokenProvider.validateToken(token)).isTrue();
  }

  @Test
  void validateToken_위변조된_토큰_false반환() {
    assertThat(jwtTokenProvider.validateToken("invalid.token.value")).isFalse();
  }

  @Test
  void extractOwnerId_AccessToken에서_정상_추출() {
    UUID ownerId = UUID.randomUUID();
    String token = jwtTokenProvider.generateAccessToken(ownerId);

    UUID extracted = jwtTokenProvider.extractOwnerId(token);

    assertThat(extracted).isEqualTo(ownerId);
  }

  @Test
  void extractOwnerId_RefreshToken에서_정상_추출() {
    UUID ownerId = UUID.randomUUID();
    String token = jwtTokenProvider.generateRefreshToken(ownerId);

    UUID extracted = jwtTokenProvider.extractOwnerId(token);

    assertThat(extracted).isEqualTo(ownerId);
  }

  @Test
  void validateToken_만료된_토큰_false반환() {
    String base64Secret = Base64.getEncoder()
        .encodeToString("test-secret-key-for-unit-tests-must-be-at-least-32-bytes".getBytes());
    JwtTokenProvider expiredProvider = new JwtTokenProvider(base64Secret, -1L, -1L);
    UUID ownerId = UUID.randomUUID();
    String expiredToken = expiredProvider.generateAccessToken(ownerId);

    assertThat(jwtTokenProvider.validateToken(expiredToken)).isFalse();
  }
}
