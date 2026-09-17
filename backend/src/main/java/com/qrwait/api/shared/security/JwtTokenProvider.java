package com.qrwait.api.shared.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider {

  private static final String CLAIM_OWNER_ID = "ownerId";
  private static final String CLAIM_LOGIN_AT = "loginAt";

  private final SecretKey secretKey;
  private final long accessExpirySeconds;
  private final long refreshIdleExpirySeconds;
  private final long refreshAbsoluteExpirySeconds;

  public JwtTokenProvider(
      @Value("${jwt.secret}") String secret,
      @Value("${jwt.access-expiry}") long accessExpirySeconds,
      @Value("${jwt.refresh-idle-expiry}") long refreshIdleExpirySeconds,
      @Value("${jwt.refresh-absolute-expiry}") long refreshAbsoluteExpirySeconds) {
    this.secretKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
    this.accessExpirySeconds = accessExpirySeconds;
    this.refreshIdleExpirySeconds = refreshIdleExpirySeconds;
    this.refreshAbsoluteExpirySeconds = refreshAbsoluteExpirySeconds;
  }

  public String generateAccessToken(UUID ownerId) {
    Date now = new Date();
    Date expiry = new Date(now.getTime() + accessExpirySeconds * 1000);
    return Jwts.builder()
        .claim(CLAIM_OWNER_ID, ownerId.toString())
        .issuedAt(now)
        .expiration(expiry)
        .signWith(secretKey)
        .compact();
  }

  public String generateRefreshToken(UUID ownerId) {
    long now = System.currentTimeMillis();
    return buildRefreshToken(ownerId, now, now).token();
  }

  /**
   * 매 refresh 호출마다 refresh token을 교체한다. idle(활동 기준)·절대 만료(최초 로그인 기준) 중
   * 먼저 도달하는 시점으로 새 토큰의 만료를 계산하고, 절대 만료를 이미 넘겼으면 로테이션을 거부한다.
   */
  public Optional<RotatedRefreshToken> rotateRefreshToken(String refreshToken) {
    Claims claims;
    try {
      claims = parseClaims(refreshToken);
    } catch (JwtException | IllegalArgumentException e) {
      return Optional.empty();
    }

    String loginAtClaim = claims.get(CLAIM_LOGIN_AT, String.class);
    if (loginAtClaim == null) {
      return Optional.empty();
    }

    long loginAtMillis = Long.parseLong(loginAtClaim);
    long now = System.currentTimeMillis();
    long absoluteDeadlineMillis = loginAtMillis + refreshAbsoluteExpirySeconds * 1000;
    if (now >= absoluteDeadlineMillis) {
      return Optional.empty();
    }

    UUID ownerId = UUID.fromString(claims.get(CLAIM_OWNER_ID, String.class));
    return Optional.of(buildRefreshToken(ownerId, loginAtMillis, now));
  }

  public boolean validateToken(String token) {
    try {
      parseClaims(token);
      return true;
    } catch (JwtException | IllegalArgumentException e) {
      return false;
    }
  }

  public UUID extractOwnerId(String token) {
    String ownerIdStr = parseClaims(token).get(CLAIM_OWNER_ID, String.class);
    return UUID.fromString(ownerIdStr);
  }

  private RotatedRefreshToken buildRefreshToken(UUID ownerId, long loginAtMillis, long nowMillis) {
    long idleDeadlineMillis = nowMillis + refreshIdleExpirySeconds * 1000;
    long absoluteDeadlineMillis = loginAtMillis + refreshAbsoluteExpirySeconds * 1000;
    long expiryMillis = Math.min(idleDeadlineMillis, absoluteDeadlineMillis);

    String token = Jwts.builder()
        .claim(CLAIM_OWNER_ID, ownerId.toString())
        .claim(CLAIM_LOGIN_AT, String.valueOf(loginAtMillis))
        .issuedAt(new Date(nowMillis))
        .expiration(new Date(expiryMillis))
        .signWith(secretKey)
        .compact();

    long ttlSeconds = Math.max(0, (expiryMillis - nowMillis) / 1000);
    return new RotatedRefreshToken(token, ttlSeconds);
  }

  private Claims parseClaims(String token) {
    return Jwts.parser()
        .verifyWith(secretKey)
        .build()
        .parseSignedClaims(token)
        .getPayload();
  }

  public record RotatedRefreshToken(String token, long ttlSeconds) {

  }
}
