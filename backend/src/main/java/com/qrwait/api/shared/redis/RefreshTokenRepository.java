package com.qrwait.api.shared.redis;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class RefreshTokenRepository {

  private static final String KEY_PREFIX = "refresh:token:";

  private final StringRedisTemplate redisTemplate;

  public void save(UUID ownerId, String refreshToken, long expirySeconds) {
    redisTemplate.opsForValue().set(buildKey(ownerId), refreshToken, expirySeconds, TimeUnit.SECONDS);
  }

  public Optional<String> findByOwnerId(UUID ownerId) {
    String token = redisTemplate.opsForValue().get(buildKey(ownerId));
    return Optional.ofNullable(token);
  }

  public void delete(UUID ownerId) {
    redisTemplate.delete(buildKey(ownerId));
  }

  private String buildKey(UUID ownerId) {
    return KEY_PREFIX + ownerId;
  }
}
