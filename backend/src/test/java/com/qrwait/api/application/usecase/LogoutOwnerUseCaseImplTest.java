package com.qrwait.api.application.usecase;

import static org.mockito.Mockito.verify;

import com.qrwait.api.infrastructure.redis.RefreshTokenRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LogoutOwnerUseCaseImplTest {

  @Mock
  private RefreshTokenRepository refreshTokenRepository;

  @InjectMocks
  private LogoutOwnerUseCaseImpl useCase;

  @Test
  void execute_Redis에서_리프레시토큰_삭제() {
    UUID ownerId = UUID.randomUUID();

    useCase.execute(ownerId);

    verify(refreshTokenRepository).delete(ownerId);
  }
}
