package com.qrwait.api.application.usecase;

import com.qrwait.api.shared.redis.RefreshTokenRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LogoutOwnerUseCaseImpl implements LogoutOwnerUseCase {

  private final RefreshTokenRepository refreshTokenRepository;

  @Override
  public void execute(UUID ownerId) {
    refreshTokenRepository.delete(ownerId);
  }
}
