package com.qrwait.api.owner.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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
import com.qrwait.api.store.domain.StoreRepository;
import com.qrwait.api.store.domain.StoreSettings;
import com.qrwait.api.store.domain.StoreSettingsRepository;
import java.time.LocalDateTime;
import java.time.LocalTime;
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
class OwnerServiceTest {

  private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
  private final UUID ownerId = UUID.randomUUID();
  private final UUID storeId = UUID.randomUUID();

  @Mock
  private OwnerRepository ownerRepository;
  @Mock
  private StoreRepository storeRepository;
  @Mock
  private StoreSettingsRepository storeSettingsRepository;
  @Mock
  private RefreshTokenRepository refreshTokenRepository;
  @Mock
  private JwtTokenProvider jwtTokenProvider;

  private OwnerService ownerService;

  @BeforeEach
  void setUp() {
    ownerService = new OwnerService(ownerRepository, storeRepository, storeSettingsRepository,
        refreshTokenRepository, jwtTokenProvider, passwordEncoder);
    ReflectionTestUtils.setField(ownerService, "baseUrl", "http://localhost:5173");
    ReflectionTestUtils.setField(ownerService, "refreshExpirySeconds", 604800L);
  }

  // ===== signUp =====

  @Test
  void signUp_정상_회원가입() {
    SignUpRequest request = createSignUpRequest("owner@test.com", "password123", "테스트 매장", "서울시 강남구");
    Owner savedOwner = Owner.restore(ownerId, "owner@test.com", "hashed", LocalDateTime.now());
    Store savedStore = Store.restore(storeId, ownerId, "테스트 매장", "서울시 강남구", null, LocalDateTime.now());
    StoreSettings savedSettings = StoreSettings.restore(UUID.randomUUID(), storeId, 5, 30, LocalTime.of(9, 0), LocalTime.of(22, 0), 10, true);

    given(ownerRepository.findByEmail("owner@test.com")).willReturn(Optional.empty());
    given(ownerRepository.save(any(Owner.class))).willReturn(savedOwner);
    given(storeRepository.save(any(Store.class))).willReturn(savedStore);
    given(storeSettingsRepository.save(any(StoreSettings.class))).willReturn(savedSettings);

    SignUpResponse response = ownerService.signUp(request);

    assertThat(response.ownerId()).isEqualTo(ownerId);
    assertThat(response.storeId()).isEqualTo(storeId);
    assertThat(response.qrUrl()).contains(storeId.toString());
  }

  @Test
  void signUp_중복_이메일_예외발생() {
    SignUpRequest request = createSignUpRequest("duplicate@test.com", "password123", "매장", "주소");
    Owner existing = Owner.restore(UUID.randomUUID(), "duplicate@test.com", "hashed", LocalDateTime.now());

    given(ownerRepository.findByEmail("duplicate@test.com")).willReturn(Optional.of(existing));

    assertThatThrownBy(() -> ownerService.signUp(request))
        .isInstanceOf(DuplicateEmailException.class);

    verify(ownerRepository, never()).save(any());
  }

  // ===== login =====

  @Test
  void login_정상_로그인() {
    String rawPassword = "password123";
    String encodedPassword = passwordEncoder.encode(rawPassword);
    Owner owner = Owner.restore(ownerId, "owner@test.com", encodedPassword, LocalDateTime.now());
    Store store = Store.restore(storeId, ownerId, "테스트 매장", "서울", null, LocalDateTime.now());

    given(ownerRepository.findByEmail("owner@test.com")).willReturn(Optional.of(owner));
    given(storeRepository.findByOwnerId(ownerId)).willReturn(Optional.of(store));
    given(jwtTokenProvider.generateAccessToken(ownerId)).willReturn("access-token");
    given(jwtTokenProvider.generateRefreshToken(ownerId)).willReturn("refresh-token");

    LoginRequest request = createLoginRequest("owner@test.com", rawPassword);
    LoginResponse response = ownerService.login(request);

    assertThat(response.accessToken()).isEqualTo("access-token");
    assertThat(response.refreshToken()).isEqualTo("refresh-token");
    assertThat(response.ownerId()).isEqualTo(ownerId);
    assertThat(response.storeId()).isEqualTo(storeId);
    verify(refreshTokenRepository).save(ownerId, "refresh-token", 604800L);
  }

  @Test
  void login_존재하지않는_이메일_예외발생() {
    given(ownerRepository.findByEmail(anyString())).willReturn(Optional.empty());

    assertThatThrownBy(() -> ownerService.login(createLoginRequest("none@test.com", "password123")))
        .isInstanceOf(InvalidCredentialsException.class);

    verify(refreshTokenRepository, never()).save(any(), anyString(), anyLong());
  }

  @Test
  void login_비밀번호_불일치_예외발생() {
    Owner owner = Owner.restore(ownerId, "owner@test.com", passwordEncoder.encode("correct"), LocalDateTime.now());
    given(ownerRepository.findByEmail("owner@test.com")).willReturn(Optional.of(owner));

    assertThatThrownBy(() -> ownerService.login(createLoginRequest("owner@test.com", "wrong-password")))
        .isInstanceOf(InvalidCredentialsException.class);

    verify(refreshTokenRepository, never()).save(any(), anyString(), anyLong());
  }

  // ===== logout =====

  @Test
  void logout_Redis에서_리프레시토큰_삭제() {
    ownerService.logout(ownerId);

    verify(refreshTokenRepository).delete(ownerId);
  }

  // ===== refresh =====

  @Test
  void refresh_유효한_토큰_새_AccessToken_발급() {
    String refreshToken = "valid-refresh-token";
    given(jwtTokenProvider.validateToken(refreshToken)).willReturn(true);
    given(jwtTokenProvider.extractOwnerId(refreshToken)).willReturn(ownerId);
    given(refreshTokenRepository.findByOwnerId(ownerId)).willReturn(Optional.of(refreshToken));
    given(jwtTokenProvider.generateAccessToken(ownerId)).willReturn("new-access-token");

    String newAccessToken = ownerService.refresh(refreshToken);

    assertThat(newAccessToken).isEqualTo("new-access-token");
  }

  @Test
  void refresh_유효하지않은_토큰_예외발생() {
    given(jwtTokenProvider.validateToken("bad-token")).willReturn(false);

    assertThatThrownBy(() -> ownerService.refresh("bad-token"))
        .isInstanceOf(InvalidCredentialsException.class);
  }

  @Test
  void refresh_Redis에_없는_토큰_예외발생() {
    String refreshToken = "valid-refresh-token";
    given(jwtTokenProvider.validateToken(refreshToken)).willReturn(true);
    given(jwtTokenProvider.extractOwnerId(refreshToken)).willReturn(ownerId);
    given(refreshTokenRepository.findByOwnerId(ownerId)).willReturn(Optional.empty());

    assertThatThrownBy(() -> ownerService.refresh(refreshToken))
        .isInstanceOf(InvalidCredentialsException.class);
  }

  @Test
  void refresh_Redis_저장값과_불일치_예외발생() {
    String refreshToken = "valid-refresh-token";
    given(jwtTokenProvider.validateToken(refreshToken)).willReturn(true);
    given(jwtTokenProvider.extractOwnerId(refreshToken)).willReturn(ownerId);
    given(refreshTokenRepository.findByOwnerId(ownerId)).willReturn(Optional.of("different-token"));

    assertThatThrownBy(() -> ownerService.refresh(refreshToken))
        .isInstanceOf(InvalidCredentialsException.class);
  }

  private SignUpRequest createSignUpRequest(String email, String password, String storeName, String address) {
    SignUpRequest request = new SignUpRequest();
    ReflectionTestUtils.setField(request, "email", email);
    ReflectionTestUtils.setField(request, "password", password);
    ReflectionTestUtils.setField(request, "storeName", storeName);
    ReflectionTestUtils.setField(request, "address", address);
    return request;
  }

  private LoginRequest createLoginRequest(String email, String password) {
    LoginRequest request = new LoginRequest();
    ReflectionTestUtils.setField(request, "email", email);
    ReflectionTestUtils.setField(request, "password", password);
    return request;
  }
}
