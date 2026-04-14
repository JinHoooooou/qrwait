package com.qrwait.api.waiting.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.verify;

import com.qrwait.api.store.domain.Store;
import com.qrwait.api.store.domain.StoreNotAvailableException;
import com.qrwait.api.store.domain.StoreNotFoundException;
import com.qrwait.api.store.domain.StoreRepository;
import com.qrwait.api.store.domain.StoreSettings;
import com.qrwait.api.store.domain.StoreSettingsRepository;
import com.qrwait.api.store.domain.StoreStatus;
import com.qrwait.api.waiting.application.dto.RegisterWaitingRequest;
import com.qrwait.api.waiting.application.dto.RegisterWaitingResponse;
import com.qrwait.api.waiting.application.dto.WaitingStatusResponse;
import com.qrwait.api.waiting.domain.WaitingEntry;
import com.qrwait.api.waiting.domain.WaitingNotFoundException;
import com.qrwait.api.waiting.domain.WaitingRepository;
import com.qrwait.api.waiting.domain.WaitingStatus;
import com.qrwait.api.waiting.domain.event.WaitingRegisteredEvent;
import com.qrwait.api.waiting.domain.event.WaitingUpdatedEvent;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class WaitingServiceTest {

  @Mock
  WaitingRepository waitingRepository;
  @Mock
  StoreRepository storeRepository;
  @Mock
  StoreSettingsRepository storeSettingsRepository;
  @Mock
  ApplicationEventPublisher eventPublisher;

  WaitingService waitingService;

  @BeforeEach
  void setUp() {
    waitingService = new WaitingService(waitingRepository, storeRepository, storeSettingsRepository, eventPublisher);
  }

  // ===== register =====

  @Test
  void register_정상등록_waitingNumber와_currentRank_반환() {
    UUID storeId = UUID.randomUUID();
    RegisterWaitingRequest request = new RegisterWaitingRequest();
    request.setVisitorName("홍길동");
    request.setPartySize(2);

    given(storeRepository.findById(storeId))
        .willReturn(Optional.of(Store.create(null, "테스트 식당", null)));
    given(waitingRepository.findNextWaitingNumber(storeId)).willReturn(3);
    given(waitingRepository.save(any(WaitingEntry.class)))
        .willAnswer(inv -> inv.getArgument(0));
    given(waitingRepository.countByStoreIdAndStatus(storeId, WaitingStatus.WAITING)).willReturn(2);

    RegisterWaitingResponse response = waitingService.register(storeId, request);

    assertThat(response.waitingNumber()).isEqualTo(3);
    assertThat(response.currentRank()).isEqualTo(2);
    assertThat(response.totalWaiting()).isEqualTo(2);
    assertThat(response.estimatedWaitMinutes()).isEqualTo(10);
    assertThat(response.waitingToken()).isNotBlank();
    then(eventPublisher).should().publishEvent(any(WaitingRegisteredEvent.class));
  }

  @Test
  void register_StoreSettings_기반_예상대기시간_계산() {
    UUID storeId = UUID.randomUUID();
    RegisterWaitingRequest request = new RegisterWaitingRequest();
    request.setVisitorName("홍길동");
    request.setPartySize(2);

    given(storeRepository.findById(storeId))
        .willReturn(Optional.of(Store.create(null, "테스트 식당", null)));
    given(waitingRepository.findNextWaitingNumber(storeId)).willReturn(1);
    given(waitingRepository.save(any(WaitingEntry.class)))
        .willAnswer(inv -> inv.getArgument(0));
    given(waitingRepository.countByStoreIdAndStatus(storeId, WaitingStatus.WAITING)).willReturn(3);
    given(storeSettingsRepository.findByStoreId(storeId))
        .willReturn(Optional.of(StoreSettings.createDefault(storeId))); // tableCount=5, avgTurnoverMinutes=30 → 30/5*3=18

    RegisterWaitingResponse response = waitingService.register(storeId, request);

    assertThat(response.estimatedWaitMinutes()).isEqualTo(18);
  }

  @Test
  void register_존재하지않는_storeId_예외발생() {
    UUID storeId = UUID.randomUUID();
    RegisterWaitingRequest request = new RegisterWaitingRequest();
    request.setVisitorName("홍길동");
    request.setPartySize(2);

    given(storeRepository.findById(storeId)).willReturn(Optional.empty());

    assertThatThrownBy(() -> waitingService.register(storeId, request))
        .isInstanceOf(StoreNotFoundException.class);
  }

  @Test
  void register_매장_OPEN아닐때_예외발생() {
    UUID storeId = UUID.randomUUID();
    RegisterWaitingRequest request = new RegisterWaitingRequest();
    request.setVisitorName("홍길동");
    request.setPartySize(2);

    Store closedStore = Store.restore(storeId, UUID.randomUUID(), "테스트 식당", "서울",
        StoreStatus.CLOSED, LocalDateTime.now());
    given(storeRepository.findById(storeId)).willReturn(Optional.of(closedStore));

    assertThatThrownBy(() -> waitingService.register(storeId, request))
        .isInstanceOf(StoreNotAvailableException.class);
  }

  // ===== getStoreWaitingStatus =====

  @Test
  void getStoreWaitingStatus_StoreSettings_기반_예상대기시간_계산() {
    UUID storeId = UUID.randomUUID();
    given(waitingRepository.countByStoreIdAndStatus(storeId, WaitingStatus.WAITING)).willReturn(4);
    given(storeSettingsRepository.findByStoreId(storeId))
        .willReturn(Optional.of(StoreSettings.createDefault(storeId))); // 30/5*4=24

    WaitingStatusResponse response = waitingService.getStoreWaitingStatus(storeId);

    assertThat(response.totalWaiting()).isEqualTo(4);
    assertThat(response.estimatedWaitMinutes()).isEqualTo(24);
  }

  @Test
  void getStoreWaitingStatus_StoreSettings_없을때_fallback_계산() {
    UUID storeId = UUID.randomUUID();
    given(waitingRepository.countByStoreIdAndStatus(storeId, WaitingStatus.WAITING)).willReturn(4);
    given(storeSettingsRepository.findByStoreId(storeId)).willReturn(Optional.empty());

    WaitingStatusResponse response = waitingService.getStoreWaitingStatus(storeId);

    assertThat(response.estimatedWaitMinutes()).isEqualTo(20); // 4 * 5
  }

  // ===== getStatus =====

  @Test
  void getStatus_currentRank_정확성_검증() {
    UUID storeId = UUID.randomUUID();
    UUID waitingId = UUID.randomUUID();

    WaitingEntry target = WaitingEntry.restore(
        waitingId, storeId, "홍길동", 2, 3, WaitingStatus.WAITING, LocalDateTime.now());

    List<WaitingEntry> waitingList = List.of(
        WaitingEntry.restore(UUID.randomUUID(), storeId, "손님A", 2, 1, WaitingStatus.WAITING, LocalDateTime.now()),
        WaitingEntry.restore(UUID.randomUUID(), storeId, "손님B", 2, 2, WaitingStatus.WAITING, LocalDateTime.now()),
        target
    );

    given(waitingRepository.findById(waitingId)).willReturn(Optional.of(target));
    given(waitingRepository.findByStoreIdAndStatus(storeId, WaitingStatus.WAITING)).willReturn(waitingList);

    WaitingStatusResponse response = waitingService.getStatus(waitingId);

    assertThat(response.currentRank()).isEqualTo(3);
    assertThat(response.totalWaiting()).isEqualTo(3);
    assertThat(response.estimatedWaitMinutes()).isEqualTo(10); // 앞 2팀 × 5분 (fallback)
  }

  @Test
  void getStatus_취소된_웨이팅_조회시_예외발생() {
    UUID waitingId = UUID.randomUUID();
    WaitingEntry cancelled = WaitingEntry.restore(
        waitingId, UUID.randomUUID(), "손님", 2, 1, WaitingStatus.CANCELLED, LocalDateTime.now());

    given(waitingRepository.findById(waitingId)).willReturn(Optional.of(cancelled));

    assertThatThrownBy(() -> waitingService.getStatus(waitingId))
        .isInstanceOf(WaitingNotFoundException.class);
  }

  // ===== cancel =====

  @Test
  void cancel_정상취소_상태가_CANCELLED로_변경됨() {
    UUID waitingId = UUID.randomUUID();
    WaitingEntry entry = WaitingEntry.restore(
        waitingId, UUID.randomUUID(), "손님", 2, 1, WaitingStatus.WAITING, LocalDateTime.now());

    given(waitingRepository.findById(waitingId)).willReturn(Optional.of(entry));
    given(waitingRepository.save(any(WaitingEntry.class))).willAnswer(inv -> inv.getArgument(0));

    waitingService.cancel(waitingId);

    ArgumentCaptor<WaitingEntry> captor = ArgumentCaptor.forClass(WaitingEntry.class);
    verify(waitingRepository).save(captor.capture());
    assertThat(captor.getValue().getStatus()).isEqualTo(WaitingStatus.CANCELLED);
    then(eventPublisher).should().publishEvent(any(WaitingUpdatedEvent.class));
  }

  @Test
  void cancel_존재하지않는_waitingId_예외발생() {
    UUID waitingId = UUID.randomUUID();
    given(waitingRepository.findById(waitingId)).willReturn(Optional.empty());

    assertThatThrownBy(() -> waitingService.cancel(waitingId))
        .isInstanceOf(WaitingNotFoundException.class);
  }

  @Test
  void cancel_이미취소된_웨이팅_재취소시_예외발생() {
    UUID waitingId = UUID.randomUUID();
    WaitingEntry entry = WaitingEntry.restore(
        waitingId, UUID.randomUUID(), "손님", 2, 1, WaitingStatus.CANCELLED, LocalDateTime.now());

    given(waitingRepository.findById(waitingId)).willReturn(Optional.of(entry));

    assertThatThrownBy(() -> waitingService.cancel(waitingId))
        .isInstanceOf(IllegalStateException.class);
  }
}
