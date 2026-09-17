package com.qrwait.api.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JwtTokenProviderTest {

  private static final long ACCESS_EXPIRY = 3600L;
  private static final long REFRESH_IDLE_EXPIRY = 604800L; // 7일
  private static final long REFRESH_ABSOLUTE_EXPIRY = 2592000L; // 30일

  private String base64Secret;
  private JwtTokenProvider jwtTokenProvider;

  @BeforeEach
  void setUp() {
    base64Secret = Base64.getEncoder()
        .encodeToString("test-secret-key-for-unit-tests-must-be-at-least-32-bytes".getBytes());
    jwtTokenProvider = new JwtTokenProvider(base64Secret, ACCESS_EXPIRY, REFRESH_IDLE_EXPIRY, REFRESH_ABSOLUTE_EXPIRY);
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
    JwtTokenProvider expiredProvider = new JwtTokenProvider(base64Secret, -1L, -1L, -1L);
    UUID ownerId = UUID.randomUUID();
    String expiredToken = expiredProvider.generateAccessToken(ownerId);

    assertThat(jwtTokenProvider.validateToken(expiredToken)).isFalse();
  }

  // ===== rotateRefreshToken =====

  @Test
  void rotateRefreshToken_유효한토큰_새토큰과idleTTL반환() {
    UUID ownerId = UUID.randomUUID();
    String refreshToken = jwtTokenProvider.generateRefreshToken(ownerId);

    Optional<JwtTokenProvider.RotatedRefreshToken> result = jwtTokenProvider.rotateRefreshToken(refreshToken);

    assertThat(result).isPresent();
    assertThat(result.get().token()).isNotBlank();
    assertThat(result.get().ttlSeconds()).isEqualTo(REFRESH_IDLE_EXPIRY);
    assertThat(jwtTokenProvider.extractOwnerId(result.get().token())).isEqualTo(ownerId);
  }

  @Test
  void rotateRefreshToken_절대만료가_idle보다_먼저오면_TTL이_절대만료까지_남은시간으로_제한() {
    long shortAbsoluteExpiry = 5L; // 절대 만료 5초
    JwtTokenProvider provider = new JwtTokenProvider(base64Secret, ACCESS_EXPIRY, REFRESH_IDLE_EXPIRY, shortAbsoluteExpiry);
    UUID ownerId = UUID.randomUUID();
    String refreshToken = provider.generateRefreshToken(ownerId);

    Optional<JwtTokenProvider.RotatedRefreshToken> result = provider.rotateRefreshToken(refreshToken);

    assertThat(result).isPresent();
    assertThat(result.get().ttlSeconds()).isLessThanOrEqualTo(shortAbsoluteExpiry);
  }

  @Test
  void rotateRefreshToken_절대만료초과_empty반환() {
    long alreadyExceededAbsoluteExpiry = -1L;
    JwtTokenProvider provider = new JwtTokenProvider(base64Secret, ACCESS_EXPIRY, REFRESH_IDLE_EXPIRY, alreadyExceededAbsoluteExpiry);
    UUID ownerId = UUID.randomUUID();
    String refreshToken = provider.generateRefreshToken(ownerId);

    Optional<JwtTokenProvider.RotatedRefreshToken> result = provider.rotateRefreshToken(refreshToken);

    assertThat(result).isEmpty();
  }

  @Test
  void rotateRefreshToken_loginAt클레임없는_레거시토큰_empty반환() {
    SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(base64Secret));
    String legacyToken = Jwts.builder()
        .claim("ownerId", UUID.randomUUID().toString())
        .issuedAt(new Date())
        .expiration(new Date(System.currentTimeMillis() + REFRESH_IDLE_EXPIRY * 1000))
        .signWith(key)
        .compact();

    Optional<JwtTokenProvider.RotatedRefreshToken> result = jwtTokenProvider.rotateRefreshToken(legacyToken);

    assertThat(result).isEmpty();
  }
}
