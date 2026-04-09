package com.qrwait.api.shared.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider {

  private static final String CLAIM_OWNER_ID = "ownerId";

  private final SecretKey secretKey;
  private final long accessExpirySeconds;
  private final long refreshExpirySeconds;

  public JwtTokenProvider(
      @Value("${jwt.secret}") String secret,
      @Value("${jwt.access-expiry}") long accessExpirySeconds,
      @Value("${jwt.refresh-expiry}") long refreshExpirySeconds) {
    this.secretKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
    this.accessExpirySeconds = accessExpirySeconds;
    this.refreshExpirySeconds = refreshExpirySeconds;
  }

  public String generateAccessToken(UUID ownerId) {
    return buildToken(ownerId, accessExpirySeconds);
  }

  public String generateRefreshToken(UUID ownerId) {
    return buildToken(ownerId, refreshExpirySeconds);
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

  private String buildToken(UUID ownerId, long expirySeconds) {
    Date now = new Date();
    Date expiry = new Date(now.getTime() + expirySeconds * 1000);

    return Jwts.builder()
        .claim(CLAIM_OWNER_ID, ownerId.toString())
        .issuedAt(now)
        .expiration(expiry)
        .signWith(secretKey)
        .compact();
  }

  private Claims parseClaims(String token) {
    return Jwts.parser()
        .verifyWith(secretKey)
        .build()
        .parseSignedClaims(token)
        .getPayload();
  }
}
