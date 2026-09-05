package com.qrwait.api.waiting.customer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.qrwait.api.store.domain.Store;
import com.qrwait.api.store.domain.StoreNotAvailableException;
import com.qrwait.api.store.domain.StoreNotFoundException;
import com.qrwait.api.store.domain.StoreRepository;
import com.qrwait.api.store.domain.StoreSettings;
import com.qrwait.api.store.domain.StoreSettingsRepository;
import com.qrwait.api.store.domain.StoreStatus;
import com.qrwait.api.waiting.customer.dto.RegisterWaitingRequest;
import com.qrwait.api.waiting.customer.dto.RegisterWaitingResponse;
import com.qrwait.api.waiting.domain.WaitingEntry;
import com.qrwait.api.waiting.domain.WaitingRepository;
import com.qrwait.api.waiting.domain.WaitingStatus;
import com.qrwait.api.waiting.domain.event.WaitingRegisteredEvent;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class WaitingRegistrarTest {

  @Mock
  WaitingRepository waitingRepository;
  @Mock
  StoreRepository storeRepository;
  @Mock
  StoreSettingsRepository storeSettingsRepository;
  @Mock
  ApplicationEventPublisher eventPublisher;

  WaitingRegistrar waitingRegistrar;

  @BeforeEach
  void setUp() {
    waitingRegistrar = new WaitingRegistrar(waitingRepository, storeRepository, storeSettingsRepository, eventPublisher);
  }

  @Test
  void registerOnce_정상등록_waitingNumber와_currentRank_반환() {
    UUID storeId = UUID.randomUUID();
    RegisterWaitingRequest request = new RegisterWaitingRequest();
    request.setPhoneNumber("010-1234-5678");
    request.setPartySize(2);

    given(storeRepository.findById(storeId))
        .willReturn(Optional.of(Store.create(null, "테스트 식당", null)));
    given(storeSettingsRepository.findByStoreId(storeId)).willReturn(Optional.empty());
    given(waitingRepository.findNextWaitingNumber(eq(storeId), any(LocalDate.class))).willReturn(3);
    given(waitingRepository.save(any(WaitingEntry.class)))
        .willAnswer(inv -> inv.getArgument(0));
    given(waitingRepository.countByStoreIdAndStatus(eq(storeId), any(LocalDate.class), eq(WaitingStatus.WAITING)))
        .willReturn(2);

    RegisterWaitingResponse response = waitingRegistrar.registerOnce(storeId, request);

    assertThat(response.waitingNumber()).isEqualTo(3);
    assertThat(response.currentRank()).isEqualTo(2);
    assertThat(response.totalWaiting()).isEqualTo(2);
    // 예상 대기시간은 본인을 뺀 앞 1팀 기준: 1 * DEFAULT_MINUTES_PER_PERSON(5) = 5
    assertThat(response.estimatedWaitMinutes()).isEqualTo(5);
    then(eventPublisher).should().publishEvent(any(WaitingRegisteredEvent.class));
  }

  @Test
  void registerOnce_StoreSettings_기반_예상대기시간_계산() {
    UUID storeId = UUID.randomUUID();
    RegisterWaitingRequest request = new RegisterWaitingRequest();
    request.setPhoneNumber("010-1234-5678");
    request.setPartySize(2);

    given(storeRepository.findById(storeId))
        .willReturn(Optional.of(Store.create(null, "테스트 식당", null)));
    given(waitingRepository.findNextWaitingNumber(eq(storeId), any(LocalDate.class))).willReturn(1);
    given(waitingRepository.save(any(WaitingEntry.class)))
        .willAnswer(inv -> inv.getArgument(0));
    given(waitingRepository.countByStoreIdAndStatus(eq(storeId), any(LocalDate.class), eq(WaitingStatus.WAITING)))
        .willReturn(3);
    given(storeSettingsRepository.findByStoreId(storeId))
        // totalWaiting=3, 본인을 뺀 앞 2팀 기준: tableCount=5, avgTurnoverMinutes=30 → round(30*2/5)=12
        .willReturn(Optional.of(StoreSettings.createDefault(storeId)));

    RegisterWaitingResponse response = waitingRegistrar.registerOnce(storeId, request);

    assertThat(response.estimatedWaitMinutes()).isEqualTo(12);
  }

  @Test
  void registerOnce_존재하지않는_storeId_예외발생() {
    UUID storeId = UUID.randomUUID();
    RegisterWaitingRequest request = new RegisterWaitingRequest();
    request.setPhoneNumber("010-1234-5678");
    request.setPartySize(2);

    given(storeRepository.findById(storeId)).willReturn(Optional.empty());

    assertThatThrownBy(() -> waitingRegistrar.registerOnce(storeId, request))
        .isInstanceOf(StoreNotFoundException.class);
  }

  @Test
  void registerOnce_매장_OPEN아닐때_예외발생() {
    UUID storeId = UUID.randomUUID();
    RegisterWaitingRequest request = new RegisterWaitingRequest();
    request.setPhoneNumber("010-1234-5678");
    request.setPartySize(2);

    Store closedStore = Store.restore(storeId, UUID.randomUUID(), "테스트 식당", "서울",
        StoreStatus.CLOSED, LocalDateTime.now());
    given(storeRepository.findById(storeId)).willReturn(Optional.of(closedStore));

    assertThatThrownBy(() -> waitingRegistrar.registerOnce(storeId, request))
        .isInstanceOf(StoreNotAvailableException.class);
  }

  @Test
  void registerOnce_매장설정_없을때_기본_영업일_기준으로_채번한다() {
    UUID storeId = UUID.randomUUID();
    RegisterWaitingRequest request = new RegisterWaitingRequest();
    request.setPhoneNumber("010-1234-5678");
    request.setPartySize(2);

    given(storeRepository.findById(storeId))
        .willReturn(Optional.of(Store.create(null, "테스트 식당", null)));
    given(storeSettingsRepository.findByStoreId(storeId)).willReturn(Optional.empty());
    given(waitingRepository.findNextWaitingNumber(eq(storeId), any(LocalDate.class))).willReturn(1);
    given(waitingRepository.save(any(WaitingEntry.class)))
        .willAnswer(inv -> inv.getArgument(0));
    given(waitingRepository.countByStoreIdAndStatus(eq(storeId), any(LocalDate.class), eq(WaitingStatus.WAITING)))
        .willReturn(1);

    RegisterWaitingResponse response = waitingRegistrar.registerOnce(storeId, request);

    assertThat(response.waitingNumber()).isEqualTo(1);
    then(waitingRepository).should().findNextWaitingNumber(eq(storeId), any(LocalDate.class));
  }
}
