package com.qrwait.api.waiting.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.verify;

import com.qrwait.api.shared.sse.SsePublisher;
import com.qrwait.api.store.domain.Store;
import com.qrwait.api.store.domain.StoreNotFoundException;
import com.qrwait.api.store.domain.StoreRepository;
import com.qrwait.api.store.domain.StoreStatus;
import com.qrwait.api.waiting.application.dto.DailySummaryResponse;
import com.qrwait.api.waiting.application.dto.OwnerWaitingResponse;
import com.qrwait.api.waiting.domain.WaitingEntry;
import com.qrwait.api.waiting.domain.WaitingNotFoundException;
import com.qrwait.api.waiting.domain.WaitingRepository;
import com.qrwait.api.waiting.domain.WaitingStatus;
import com.qrwait.api.waiting.domain.event.WaitingCalledEvent;
import com.qrwait.api.waiting.domain.event.WaitingUpdatedEvent;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class WaitingManagementServiceTest {

  private final UUID ownerId = UUID.randomUUID();
  private final UUID storeId = UUID.randomUUID();
  private final UUID waitingId = UUID.randomUUID();

  @Mock
  WaitingRepository waitingRepository;
  @Mock
  StoreRepository storeRepository;
  @Mock
  SsePublisher ssePublisher;
  @Mock
  ApplicationEventPublisher eventPublisher;

  WaitingManagementService service;

  @BeforeEach
  void setUp() {
    service = new WaitingManagementService(waitingRepository, storeRepository, ssePublisher, eventPublisher);
  }

  // ===== getWaitingList =====

  @Test
  void getWaitingList_대기_목록_반환() {
    WaitingEntry waiting = WaitingEntry.restore(UUID.randomUUID(), storeId, "010-1111-0001", 2, 1,
        WaitingStatus.WAITING, LocalDateTime.now().minusMinutes(10));
    WaitingEntry called = WaitingEntry.restore(UUID.randomUUID(), storeId, "010-1111-0002", 3, 2,
        WaitingStatus.CALLED, LocalDateTime.now().minusMinutes(5));

    given(storeRepository.findByOwnerId(ownerId))
        .willReturn(Optional.of(Store.restore(storeId, ownerId, "테스트 매장", "서울", StoreStatus.OPEN, LocalDateTime.now())));
    given(waitingRepository.findActiveByStoreId(storeId)).willReturn(List.of(waiting, called));

    List<OwnerWaitingResponse> result = service.getWaitingList(ownerId);

    assertThat(result).hasSize(2);
    assertThat(result.get(0).phoneNumber()).isEqualTo("010-1111-0001");
    assertThat(result.get(0).status()).isEqualTo(WaitingStatus.WAITING);
    assertThat(result.get(0).elapsedMinutes()).isGreaterThanOrEqualTo(10);
    assertThat(result.get(1).phoneNumber()).isEqualTo("010-1111-0002");
    assertThat(result.get(1).status()).isEqualTo(WaitingStatus.CALLED);
  }

  @Test
  void getWaitingList_활성_대기_없음_빈_목록_반환() {
    given(storeRepository.findByOwnerId(ownerId))
        .willReturn(Optional.of(Store.restore(storeId, ownerId, "테스트 매장", "서울", StoreStatus.OPEN, LocalDateTime.now())));
    given(waitingRepository.findActiveByStoreId(storeId)).willReturn(List.of());

    List<OwnerWaitingResponse> result = service.getWaitingList(ownerId);

    assertThat(result).isEmpty();
  }

  // ===== getDailySummary =====

  @Test
  void getDailySummary_일별_통계_집계() {
    given(storeRepository.findByOwnerId(ownerId))
        .willReturn(Optional.of(Store.restore(storeId, ownerId, "테스트 매장", "서울", StoreStatus.OPEN, LocalDateTime.now())));

    given(waitingRepository.countByStoreIdAndStatusAndDate(eq(storeId), eq(WaitingStatus.WAITING), any())).willReturn(3L);
    given(waitingRepository.countByStoreIdAndStatusAndDate(eq(storeId), eq(WaitingStatus.CALLED), any())).willReturn(1L);
    given(waitingRepository.countByStoreIdAndStatusAndDate(eq(storeId), eq(WaitingStatus.ENTERED), any())).willReturn(5L);
    given(waitingRepository.countByStoreIdAndStatusAndDate(eq(storeId), eq(WaitingStatus.NO_SHOW), any())).willReturn(2L);
    given(waitingRepository.countByStoreIdAndStatusAndDate(eq(storeId), eq(WaitingStatus.CANCELLED), any())).willReturn(1L);

    DailySummaryResponse response = service.getDailySummary(ownerId);

    assertThat(response.totalRegistered()).isEqualTo(12);
    assertThat(response.totalEntered()).isEqualTo(5);
    assertThat(response.totalNoShow()).isEqualTo(2);
    assertThat(response.totalCancelled()).isEqualTo(1);
    assertThat(response.currentWaiting()).isEqualTo(4);
  }

  @Test
  void getDailySummary_데이터_없을_때_모두_0() {
    given(storeRepository.findByOwnerId(ownerId))
        .willReturn(Optional.of(Store.restore(storeId, ownerId, "테스트 매장", "서울", StoreStatus.OPEN, LocalDateTime.now())));
    given(waitingRepository.countByStoreIdAndStatusAndDate(eq(storeId), any(), any())).willReturn(0L);

    DailySummaryResponse response = service.getDailySummary(ownerId);

    assertThat(response.totalRegistered()).isZero();
    assertThat(response.currentWaiting()).isZero();
  }

  // ===== call =====

  @Test
  void call_정상_호출처리() {
    WaitingEntry entry = WaitingEntry.restore(waitingId, storeId, "010-1111-0001", 2, 1, WaitingStatus.WAITING, LocalDateTime.now());
    given(waitingRepository.findById(waitingId)).willReturn(Optional.of(entry));
    given(storeRepository.findByOwnerId(ownerId))
        .willReturn(Optional.of(Store.restore(storeId, ownerId, "테스트 매장", "서울", StoreStatus.OPEN, LocalDateTime.now())));
    given(waitingRepository.save(any())).willReturn(entry);

    service.call(ownerId, waitingId);

    verify(waitingRepository).save(any());
    then(eventPublisher).should().publishEvent(any(WaitingCalledEvent.class));
  }

  @Test
  void call_소유권_불일치_예외발생() {
    UUID otherStoreId = UUID.randomUUID();
    WaitingEntry entry = WaitingEntry.restore(waitingId, storeId, "010-1111-0001", 2, 1, WaitingStatus.WAITING, LocalDateTime.now());
    given(waitingRepository.findById(waitingId)).willReturn(Optional.of(entry));
    given(storeRepository.findByOwnerId(ownerId))
        .willReturn(Optional.of(Store.restore(otherStoreId, ownerId, "내 매장", "서울", StoreStatus.OPEN, LocalDateTime.now())));

    assertThatThrownBy(() -> service.call(ownerId, waitingId))
        .isInstanceOf(StoreNotFoundException.class);
  }

  @Test
  void call_존재하지않는_대기_예외발생() {
    given(waitingRepository.findById(waitingId)).willReturn(Optional.empty());

    assertThatThrownBy(() -> service.call(ownerId, waitingId))
        .isInstanceOf(WaitingNotFoundException.class);
  }

  // ===== enter =====

  @Test
  void enter_정상_입장처리() {
    WaitingEntry entry = WaitingEntry.restore(waitingId, storeId, "010-1111-0001", 2, 1, WaitingStatus.CALLED, LocalDateTime.now());
    given(waitingRepository.findById(waitingId)).willReturn(Optional.of(entry));
    given(storeRepository.findByOwnerId(ownerId))
        .willReturn(Optional.of(Store.restore(storeId, ownerId, "테스트 매장", "서울", StoreStatus.OPEN, LocalDateTime.now())));
    given(waitingRepository.save(any())).willReturn(entry);

    service.enter(ownerId, waitingId);

    verify(waitingRepository).save(any());
    then(eventPublisher).should().publishEvent(any(WaitingUpdatedEvent.class));
  }

  @Test
  void enter_소유권_불일치_예외발생() {
    UUID otherStoreId = UUID.randomUUID();
    WaitingEntry entry = WaitingEntry.restore(waitingId, storeId, "010-1111-0001", 2, 1, WaitingStatus.CALLED, LocalDateTime.now());
    given(waitingRepository.findById(waitingId)).willReturn(Optional.of(entry));
    given(storeRepository.findByOwnerId(ownerId))
        .willReturn(Optional.of(Store.restore(otherStoreId, ownerId, "내 매장", "서울", StoreStatus.OPEN, LocalDateTime.now())));

    assertThatThrownBy(() -> service.enter(ownerId, waitingId))
        .isInstanceOf(StoreNotFoundException.class);
  }

  @Test
  void enter_존재하지않는_대기_예외발생() {
    given(waitingRepository.findById(waitingId)).willReturn(Optional.empty());

    assertThatThrownBy(() -> service.enter(ownerId, waitingId))
        .isInstanceOf(WaitingNotFoundException.class);
  }

  // ===== noShow =====

  @Test
  void noShow_정상_노쇼처리() {
    WaitingEntry entry = WaitingEntry.restore(waitingId, storeId, "010-1111-0001", 2, 1, WaitingStatus.CALLED, LocalDateTime.now());
    given(waitingRepository.findById(waitingId)).willReturn(Optional.of(entry));
    given(storeRepository.findByOwnerId(ownerId))
        .willReturn(Optional.of(Store.restore(storeId, ownerId, "테스트 매장", "서울", StoreStatus.OPEN, LocalDateTime.now())));
    given(waitingRepository.save(any())).willReturn(entry);

    service.noShow(ownerId, waitingId);

    verify(waitingRepository).save(any());
    then(eventPublisher).should().publishEvent(any(WaitingUpdatedEvent.class));
  }

  @Test
  void noShow_소유권_불일치_예외발생() {
    UUID otherStoreId = UUID.randomUUID();
    WaitingEntry entry = WaitingEntry.restore(waitingId, storeId, "010-1111-0001", 2, 1, WaitingStatus.CALLED, LocalDateTime.now());
    given(waitingRepository.findById(waitingId)).willReturn(Optional.of(entry));
    given(storeRepository.findByOwnerId(ownerId))
        .willReturn(Optional.of(Store.restore(otherStoreId, ownerId, "내 매장", "서울", StoreStatus.OPEN, LocalDateTime.now())));

    assertThatThrownBy(() -> service.noShow(ownerId, waitingId))
        .isInstanceOf(StoreNotFoundException.class);
  }

  @Test
  void noShow_존재하지않는_대기_예외발생() {
    given(waitingRepository.findById(waitingId)).willReturn(Optional.empty());

    assertThatThrownBy(() -> service.noShow(ownerId, waitingId))
        .isInstanceOf(WaitingNotFoundException.class);
  }
}
