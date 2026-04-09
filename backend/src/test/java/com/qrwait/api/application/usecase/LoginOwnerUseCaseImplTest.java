package com.qrwait.api.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.qrwait.api.application.dto.LoginRequest;
import com.qrwait.api.application.dto.LoginResponse;
import com.qrwait.api.domain.model.InvalidCredentialsException;
import com.qrwait.api.domain.model.Owner;
import com.qrwait.api.domain.model.Store;
import com.qrwait.api.domain.repository.OwnerRepository;
import com.qrwait.api.domain.repository.StoreRepository;
import com.qrwait.api.shared.redis.RefreshTokenRepository;
import com.qrwait.api.shared.security.JwtTokenProvider;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class LoginOwnerUseCaseImplTest {

  private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
  private final UUID ownerId = UUID.randomUUID();
  private final UUID storeId = UUID.randomUUID();
  @Mock
  private OwnerRepository ownerRepository;
  @Mock
  private StoreRepository storeRepository;
  @Mock
  private RefreshTokenRepository refreshTokenRepository;
  @Mock
  private JwtTokenProvider jwtTokenProvider;
  private LoginOwnerUseCaseImpl useCase;

  @BeforeEach
  void setUp() {
    useCase = new LoginOwnerUseCaseImpl(ownerRepository, storeRepository, refreshTokenRepository, jwtTokenProvider, passwordEncoder);
    ReflectionTestUtils.setField(useCase, "refreshExpirySeconds", 604800L);
  }

  @Test
  void execute_정상_로그인() {
    String rawPassword = "password123";
    String encodedPassword = passwordEncoder.encode(rawPassword);
    Owner owner = Owner.restore(ownerId, "owner@test.com", encodedPassword, LocalDateTime.now());
    Store store = Store.restore(storeId, ownerId, "테스트 매장", "서울", null, LocalDateTime.now());

    given(ownerRepository.findByEmail("owner@test.com")).willReturn(Optional.of(owner));
    given(storeRepository.findByOwnerId(ownerId)).willReturn(Optional.of(store));
    given(jwtTokenProvider.generateAccessToken(ownerId)).willReturn("access-token");
    given(jwtTokenProvider.generateRefreshToken(ownerId)).willReturn("refresh-token");

    LoginRequest request = createRequest("owner@test.com", rawPassword);
    LoginResponse response = useCase.execute(request);

    assertThat(response.accessToken()).isEqualTo("access-token");
    assertThat(response.refreshToken()).isEqualTo("refresh-token");
    assertThat(response.ownerId()).isEqualTo(ownerId);
    assertThat(response.storeId()).isEqualTo(storeId);
    verify(refreshTokenRepository).save(ownerId, "refresh-token", 604800L);
  }

  @Test
  void execute_존재하지않는_이메일_예외발생() {
    given(ownerRepository.findByEmail(anyString())).willReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.execute(createRequest("none@test.com", "password123")))
        .isInstanceOf(InvalidCredentialsException.class);

    verify(refreshTokenRepository, never()).save(any(), anyString(), anyLong());
  }

  @Test
  void execute_비밀번호_불일치_예외발생() {
    Owner owner = Owner.restore(ownerId, "owner@test.com", passwordEncoder.encode("correct"), LocalDateTime.now());
    given(ownerRepository.findByEmail("owner@test.com")).willReturn(Optional.of(owner));

    assertThatThrownBy(() -> useCase.execute(createRequest("owner@test.com", "wrong-password")))
        .isInstanceOf(InvalidCredentialsException.class);

    verify(refreshTokenRepository, never()).save(any(), anyString(), anyLong());
  }

  private LoginRequest createRequest(String email, String password) {
    LoginRequest request = new LoginRequest();
    ReflectionTestUtils.setField(request, "email", email);
    ReflectionTestUtils.setField(request, "password", password);
    return request;
  }
}
