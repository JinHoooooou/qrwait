package com.qrwait.api.application.usecase;

import com.qrwait.api.domain.model.InvalidCredentialsException;
import com.qrwait.api.shared.redis.RefreshTokenRepository;
import com.qrwait.api.shared.security.JwtTokenProvider;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RefreshTokenUseCaseImpl implements RefreshTokenUseCase {

  private final JwtTokenProvider jwtTokenProvider;
  private final RefreshTokenRepository refreshTokenRepository;

  @Override
  public String execute(String refreshToken) {
    if (!jwtTokenProvider.validateToken(refreshToken)) {
      throw new InvalidCredentialsException();
    }

    UUID ownerId = jwtTokenProvider.extractOwnerId(refreshToken);

    String storedToken = refreshTokenRepository.findByOwnerId(ownerId)
        .orElseThrow(InvalidCredentialsException::new);

    if (!storedToken.equals(refreshToken)) {
      throw new InvalidCredentialsException();
    }

    return jwtTokenProvider.generateAccessToken(ownerId);
  }
}
