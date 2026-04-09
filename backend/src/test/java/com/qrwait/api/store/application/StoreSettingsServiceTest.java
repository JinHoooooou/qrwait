package com.qrwait.api.store.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.qrwait.api.store.application.dto.StoreSettingsResponse;
import com.qrwait.api.store.application.dto.UpdateStoreSettingsRequest;
import com.qrwait.api.store.domain.Store;
import com.qrwait.api.store.domain.StoreNotFoundException;
import com.qrwait.api.store.domain.StoreRepository;
import com.qrwait.api.store.domain.StoreSettings;
import com.qrwait.api.store.domain.StoreSettingsRepository;
import com.qrwait.api.store.domain.StoreStatus;
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
class StoreSettingsServiceTest {

  private final UUID ownerId = UUID.randomUUID();
  private final UUID storeId = UUID.randomUUID();

  @Mock
  StoreRepository storeRepository;
  @Mock
  StoreSettingsRepository storeSettingsRepository;

  StoreSettingsService storeSettingsService;

  @BeforeEach
  void setUp() {
    storeSettingsService = new StoreSettingsService(storeRepository, storeSettingsRepository);
  }

  @Test
  void getSettings_정상_조회() {
    Store store = Store.restore(storeId, ownerId, "테스트 매장", "서울", StoreStatus.OPEN, LocalDateTime.now());
    StoreSettings settings = StoreSettings.createDefault(storeId);

    given(storeRepository.findByOwnerId(ownerId)).willReturn(Optional.of(store));
    given(storeSettingsRepository.findByStoreId(storeId)).willReturn(Optional.of(settings));

    StoreSettingsResponse response = storeSettingsService.getSettings(ownerId);

    assertThat(response.tableCount()).isEqualTo(5);
    assertThat(response.avgTurnoverMinutes()).isEqualTo(30);
    assertThat(response.alertEnabled()).isTrue();
  }

  @Test
  void getSettings_매장_없음_예외발생() {
    given(storeRepository.findByOwnerId(ownerId)).willReturn(Optional.empty());

    assertThatThrownBy(() -> storeSettingsService.getSettings(ownerId))
        .isInstanceOf(StoreNotFoundException.class);
  }

  @Test
  void updateSettings_정상_업데이트() {
    Store store = Store.restore(storeId, ownerId, "테스트 매장", "서울", StoreStatus.OPEN, LocalDateTime.now());
    StoreSettings settings = StoreSettings.createDefault(storeId);
    StoreSettings updated = settings.update(10, 20, LocalTime.of(9, 0), LocalTime.of(22, 0), 5, false);

    given(storeRepository.findByOwnerId(ownerId)).willReturn(Optional.of(store));
    given(storeSettingsRepository.findByStoreId(storeId)).willReturn(Optional.of(settings));
    given(storeSettingsRepository.save(any())).willReturn(updated);

    UpdateStoreSettingsRequest request = new UpdateStoreSettingsRequest();
    ReflectionTestUtils.setField(request, "tableCount", 10);
    ReflectionTestUtils.setField(request, "avgTurnoverMinutes", 20);
    ReflectionTestUtils.setField(request, "openTime", LocalTime.of(9, 0));
    ReflectionTestUtils.setField(request, "closeTime", LocalTime.of(22, 0));
    ReflectionTestUtils.setField(request, "alertThreshold", 5);
    ReflectionTestUtils.setField(request, "alertEnabled", false);

    StoreSettingsResponse response = storeSettingsService.updateSettings(ownerId, request);

    assertThat(response.tableCount()).isEqualTo(10);
    assertThat(response.avgTurnoverMinutes()).isEqualTo(20);
    assertThat(response.alertEnabled()).isFalse();
  }
}
