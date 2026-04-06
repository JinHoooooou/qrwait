package com.qrwait.api.infrastructure.redis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.redis.DataRedisTest;
import org.springframework.context.annotation.Import;

@DataRedisTest
@Import(RefreshTokenRepository.class)
class RefreshTokenRepositoryTest {

  @Autowired
  private RefreshTokenRepository refreshTokenRepository;

  private UUID ownerId;

  @BeforeEach
  void setUp() {
    ownerId = UUID.randomUUID();
    refreshTokenRepository.delete(ownerId); // 테스트 격리
  }

  @Test
  void save_후_findByOwnerId_정상조회() {
    refreshTokenRepository.save(ownerId, "sample-refresh-token", 604800L);

    Optional<String> result = refreshTokenRepository.findByOwnerId(ownerId);

    assertThat(result).isPresent();
    assertThat(result.get()).isEqualTo("sample-refresh-token");
  }

  @Test
  void findByOwnerId_존재하지않으면_empty반환() {
    Optional<String> result = refreshTokenRepository.findByOwnerId(UUID.randomUUID());

    assertThat(result).isEmpty();
  }

  @Test
  void delete_후_findByOwnerId_empty반환() {
    refreshTokenRepository.save(ownerId, "sample-refresh-token", 604800L);

    refreshTokenRepository.delete(ownerId);

    assertThat(refreshTokenRepository.findByOwnerId(ownerId)).isEmpty();
  }

  @Test
  void save_재호출시_토큰_덮어쓰기() {
    refreshTokenRepository.save(ownerId, "old-token", 604800L);
    refreshTokenRepository.save(ownerId, "new-token", 604800L);

    Optional<String> result = refreshTokenRepository.findByOwnerId(ownerId);

    assertThat(result).isPresent();
    assertThat(result.get()).isEqualTo("new-token");
  }
}
