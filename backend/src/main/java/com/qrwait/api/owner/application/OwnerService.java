package com.qrwait.api.owner.application;

import com.qrwait.api.owner.application.dto.LoginRequest;
import com.qrwait.api.owner.application.dto.LoginResponse;
import com.qrwait.api.owner.application.dto.SignUpRequest;
import com.qrwait.api.owner.application.dto.SignUpResponse;
import com.qrwait.api.owner.domain.DuplicateEmailException;
import com.qrwait.api.owner.domain.InvalidCredentialsException;
import com.qrwait.api.owner.domain.Owner;
import com.qrwait.api.owner.domain.OwnerRepository;
import com.qrwait.api.shared.redis.RefreshTokenRepository;
import com.qrwait.api.shared.security.JwtTokenProvider;
import com.qrwait.api.store.domain.Store;
import com.qrwait.api.store.domain.StoreNotFoundException;
import com.qrwait.api.store.domain.StoreRepository;
import com.qrwait.api.store.domain.StoreSettings;
import com.qrwait.api.store.domain.StoreSettingsRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OwnerService {

  private final OwnerRepository ownerRepository;
  private final StoreRepository storeRepository;
  private final StoreSettingsRepository storeSettingsRepository;
  private final RefreshTokenRepository refreshTokenRepository;
  private final JwtTokenProvider jwtTokenProvider;
  private final PasswordEncoder passwordEncoder;

  @Value("${app.base-url}")
  private String baseUrl;

  @Value("${jwt.refresh-expiry}")
  private long refreshExpirySeconds;

  @Transactional
  public SignUpResponse signUp(SignUpRequest request) {
    if (ownerRepository.findByEmail(request.getEmail()).isPresent()) {
      throw new DuplicateEmailException(request.getEmail());
    }

    String passwordHash = passwordEncoder.encode(request.getPassword());
    Owner owner = ownerRepository.save(Owner.create(request.getEmail(), passwordHash));

    Store store = storeRepository.save(Store.create(owner.getId(), request.getStoreName(), request.getAddress()));
    storeSettingsRepository.save(StoreSettings.createDefault(store.getId()));

    String qrUrl = baseUrl + "/wait?storeId=" + store.getId();
    return new SignUpResponse(owner.getId(), store.getId(), qrUrl);
  }

  public LoginResponse login(LoginRequest request) {
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

  public void logout(UUID ownerId) {
    refreshTokenRepository.delete(ownerId);
  }

  public String refresh(String refreshToken) {
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
