package com.qrwait.api.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.qrwait.api.application.dto.StoreSettingsResponse;
import com.qrwait.api.application.dto.UpdateStoreSettingsRequest;
import com.qrwait.api.domain.model.Store;
import com.qrwait.api.domain.model.StoreNotFoundException;
import com.qrwait.api.domain.model.StoreSettings;
import com.qrwait.api.domain.model.StoreStatus;
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
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class UpdateStoreSettingsUseCaseImplTest {

  private final UUID ownerId = UUID.randomUUID();
  private final UUID storeId = UUID.randomUUID();
  @Mock
  private StoreRepository storeRepository;
  @Mock
  private StoreSettingsRepository storeSettingsRepository;
  private UpdateStoreSettingsUseCaseImpl useCase;

  @BeforeEach
  void setUp() {
    useCase = new UpdateStoreSettingsUseCaseImpl(storeRepository, storeSettingsRepository);
  }

  @Test
  void execute_정상_설정_업데이트() {
    Store store = Store.restore(storeId, ownerId, "테스트 매장", "서울", StoreStatus.OPEN, LocalDateTime.now());
    StoreSettings settings = StoreSettings.createDefault(storeId);
    StoreSettings updated = settings.update(10, 20, LocalTime.of(9, 0), LocalTime.of(22, 0), 5, false);

    given(storeRepository.findById(storeId)).willReturn(Optional.of(store));
    given(storeSettingsRepository.findByStoreId(storeId)).willReturn(Optional.of(settings));
    given(storeSettingsRepository.save(any())).willReturn(updated);

    UpdateStoreSettingsRequest request = createRequest(10, 20, LocalTime.of(9, 0), LocalTime.of(22, 0), 5, false);
    StoreSettingsResponse response = useCase.execute(ownerId, storeId, request);

    assertThat(response.tableCount()).isEqualTo(10);
    assertThat(response.avgTurnoverMinutes()).isEqualTo(20);
    assertThat(response.alertEnabled()).isFalse();
  }

  @Test
  void execute_소유권_불일치_예외발생() {
    UUID otherOwnerId = UUID.randomUUID();
    Store store = Store.restore(storeId, otherOwnerId, "남의 매장", "부산", StoreStatus.OPEN, LocalDateTime.now());
    given(storeRepository.findById(storeId)).willReturn(Optional.of(store));

    UpdateStoreSettingsRequest request = createRequest(10, 20, null, null, 5, true);

    assertThatThrownBy(() -> useCase.execute(ownerId, storeId, request))
        .isInstanceOf(StoreNotFoundException.class);
  }

  @Test
  void execute_존재하지않는_매장_예외발생() {
    given(storeRepository.findById(storeId)).willReturn(Optional.empty());

    UpdateStoreSettingsRequest request = createRequest(10, 20, null, null, 5, true);

    assertThatThrownBy(() -> useCase.execute(ownerId, storeId, request))
        .isInstanceOf(StoreNotFoundException.class);
  }

  private UpdateStoreSettingsRequest createRequest(int tableCount, int avgTurnoverMinutes,
      LocalTime openTime, LocalTime closeTime, int alertThreshold, boolean alertEnabled) {
    UpdateStoreSettingsRequest request = new UpdateStoreSettingsRequest();
    ReflectionTestUtils.setField(request, "tableCount", tableCount);
    ReflectionTestUtils.setField(request, "avgTurnoverMinutes", avgTurnoverMinutes);
    ReflectionTestUtils.setField(request, "openTime", openTime);
    ReflectionTestUtils.setField(request, "closeTime", closeTime);
    ReflectionTestUtils.setField(request, "alertThreshold", alertThreshold);
    ReflectionTestUtils.setField(request, "alertEnabled", alertEnabled);
    return request;
  }
}
