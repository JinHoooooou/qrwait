package com.qrwait.api.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.qrwait.api.application.dto.SignUpRequest;
import com.qrwait.api.application.dto.SignUpResponse;
import com.qrwait.api.domain.model.DuplicateEmailException;
import com.qrwait.api.domain.model.Owner;
import com.qrwait.api.domain.model.Store;
import com.qrwait.api.domain.model.StoreSettings;
import com.qrwait.api.domain.repository.OwnerRepository;
import com.qrwait.api.domain.repository.StoreRepository;
import com.qrwait.api.domain.repository.StoreSettingsRepository;
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
class SignUpOwnerUseCaseImplTest {

  private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
  @Mock
  private OwnerRepository ownerRepository;
  @Mock
  private StoreRepository storeRepository;
  @Mock
  private StoreSettingsRepository storeSettingsRepository;
  private SignUpOwnerUseCaseImpl useCase;

  @BeforeEach
  void setUp() {
    useCase = new SignUpOwnerUseCaseImpl(ownerRepository, storeRepository, storeSettingsRepository, passwordEncoder);
    ReflectionTestUtils.setField(useCase, "baseUrl", "http://localhost:5173");
  }

  @Test
  void execute_정상_회원가입() {
    SignUpRequest request = createRequest("owner@test.com", "password123", "테스트 매장", "서울시 강남구");
    UUID ownerId = UUID.randomUUID();
    UUID storeId = UUID.randomUUID();
    Owner savedOwner = Owner.restore(ownerId, "owner@test.com", "hashed", LocalDateTime.now());
    Store savedStore = Store.restore(storeId, ownerId, "테스트 매장", "서울시 강남구", null, LocalDateTime.now());
    StoreSettings savedSettings = StoreSettings.restore(UUID.randomUUID(), storeId, 5, 30, LocalTime.of(9, 0), LocalTime.of(22, 0), 10, true);

    given(ownerRepository.findByEmail("owner@test.com")).willReturn(Optional.empty());
    given(ownerRepository.save(any(Owner.class))).willReturn(savedOwner);
    given(storeRepository.save(any(Store.class))).willReturn(savedStore);
    given(storeSettingsRepository.save(any(StoreSettings.class))).willReturn(savedSettings);

    SignUpResponse response = useCase.execute(request);

    assertThat(response.ownerId()).isEqualTo(ownerId);
    assertThat(response.storeId()).isEqualTo(storeId);
    assertThat(response.qrUrl()).contains(storeId.toString());
  }

  @Test
  void execute_중복_이메일_예외발생() {
    SignUpRequest request = createRequest("duplicate@test.com", "password123", "매장", "주소");
    Owner existing = Owner.restore(UUID.randomUUID(), "duplicate@test.com", "hashed", LocalDateTime.now());

    given(ownerRepository.findByEmail("duplicate@test.com")).willReturn(Optional.of(existing));

    assertThatThrownBy(() -> useCase.execute(request))
        .isInstanceOf(DuplicateEmailException.class);

    verify(ownerRepository, never()).save(any());
  }

  private SignUpRequest createRequest(String email, String password, String storeName, String address) {
    SignUpRequest request = new SignUpRequest();
    ReflectionTestUtils.setField(request, "email", email);
    ReflectionTestUtils.setField(request, "password", password);
    ReflectionTestUtils.setField(request, "storeName", storeName);
    ReflectionTestUtils.setField(request, "address", address);
    return request;
  }
}
