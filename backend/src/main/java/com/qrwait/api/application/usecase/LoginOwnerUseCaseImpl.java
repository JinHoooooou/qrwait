package com.qrwait.api.application.usecase;

import com.qrwait.api.application.dto.LoginRequest;
import com.qrwait.api.application.dto.LoginResponse;
import com.qrwait.api.domain.model.InvalidCredentialsException;
import com.qrwait.api.domain.model.Owner;
import com.qrwait.api.domain.repository.OwnerRepository;
import com.qrwait.api.shared.redis.RefreshTokenRepository;
import com.qrwait.api.shared.security.JwtTokenProvider;
import com.qrwait.api.store.domain.Store;
import com.qrwait.api.store.domain.StoreNotFoundException;
import com.qrwait.api.store.domain.StoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LoginOwnerUseCaseImpl implements LoginOwnerUseCase {

  private final OwnerRepository ownerRepository;
  private final StoreRepository storeRepository;
  private final RefreshTokenRepository refreshTokenRepository;
  private final JwtTokenProvider jwtTokenProvider;
  private final PasswordEncoder passwordEncoder;

  @Value("${jwt.refresh-expiry}")
  private long refreshExpirySeconds;

  @Override
  public LoginResponse execute(LoginRequest request) {
    Owner owner = ownerRepository.findByEmail(request.getEmail())
        .orElseThrow(InvalidCredentialsException::new);

    if (!passwordEncoder.matches(request.getPassword(), owner.getPasswordHash())) {
      throw new InvalidCredentialsException();
    }

    Store store = storeRepository.findByOwnerId(owner.getId())
        .orElseThrow(() -> new StoreNotFoundException("ownerId=" + owner.getId()));

    String accessToken = jwtTokenProvider.generateAccessToken(owner.getId());
    String refreshToken = jwtTokenProvider.generateRefreshToken(owner.getId());
    refreshTokenRepository.save(owner.getId(), refreshToken, refreshExpirySeconds);

    return new LoginResponse(accessToken, refreshToken, owner.getId(), store.getId());
  }
}
