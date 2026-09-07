package com.qrwait.api.waiting.management.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.qrwait.api.shared.sse.SsePublisher;
import com.qrwait.api.store.domain.Store;
import com.qrwait.api.store.domain.StoreNotFoundException;
import com.qrwait.api.store.domain.StoreRepository;
import com.qrwait.api.store.domain.StoreSettings;
import com.qrwait.api.store.domain.StoreSettingsRepository;
import com.qrwait.api.store.domain.StoreStatus;
import com.qrwait.api.waiting.management.dto.DailySummaryResponse;
import com.qrwait.api.waiting.management.dto.OwnerWaitingResponse;
import com.qrwait.api.waiting.management.dto.TodayWaitingResponse;
import com.qrwait.api.waiting.domain.WaitingEntry;
import com.qrwait.api.waiting.domain.WaitingNotFoundException;
import com.qrwait.api.waiting.domain.WaitingRepository;
import com.qrwait.api.waiting.domain.WaitingStatus;
import com.qrwait.api.waiting.domain.event.WaitingCalledEvent;
import com.qrwait.api.waiting.domain.event.WaitingPostponedEvent;
import com.qrwait.api.waiting.domain.event.WaitingUpdatedEvent;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
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
class WaitingManagementServiceTest {

  private final UUID ownerId = UUID.randomUUID();
  private final UUID storeId = UUID.randomUUID();
  private final UUID waitingId = UUID.randomUUID();

  @Mock
  WaitingRepository waitingRepository;
  @Mock
  StoreRepository storeRepository;
  @Mock
  StoreSettingsRepository storeSettingsRepository;
  @Mock
  SsePublisher ssePublisher;
  @Mock
  ApplicationEventPublisher eventPublisher;

  WaitingManagementService service;

  @BeforeEach
  void setUp() {
    service = new WaitingManagementService(
        waitingRepository, storeRepository, storeSettingsRepository, ssePublisher, eventPublisher);
  }

  // ===== getWaitingList =====

  @Test
  void getWaitingList_대기_목록_반환() {
    WaitingEntry waiting = WaitingEntry.restore(UUID.randomUUID(), storeId, "010-1111-0001", 2, 1,
        WaitingStatus.WAITING, LocalDateTime.now().minusMinutes(10),
        LocalDate.now(), null, null, null);
    WaitingEntry called = WaitingEntry.restore(UUID.randomUUID(), storeId, "010-1111-0002", 3, 2,
        WaitingStatus.CALLED, LocalDateTime.now().minusMinutes(5),
        LocalDate.now(), null, null, null);

    given(storeRepository.getByOwnerId(ownerId))
        .willReturn(Store.restore(storeId, ownerId, "테스트 매장", "서울", StoreStatus.OPEN, LocalDateTime.now()));
    given(storeSettingsRepository.findByStoreId(storeId))
        .willReturn(Optional.of(StoreSettings.createDefault(storeId)));
    given(waitingRepository.findActiveByStoreId(eq(storeId), any(LocalDate.class)))
        .willReturn(List.of(waiting, called));

    List<OwnerWaitingResponse> result = service.getWaitingList(ownerId);

    assertThat(result).hasSize(2);
    assertThat(result.get(0).phoneNumber()).isEqualTo("****-0001");
    assertThat(result.get(0).status()).isEqualTo(WaitingStatus.WAITING);
    assertThat(result.get(0).elapsedMinutes()).isGreaterThanOrEqualTo(10);
    // WAITING 상태(calledAt == null)이므로 graceDeadline은 null
    assertThat(result.get(0).graceDeadline()).isNull();
    assertThat(result.get(1).phoneNumber()).isEqualTo("****-0002");
    assertThat(result.get(1).status()).isEqualTo(WaitingStatus.CALLED);
  }

  @Test
  void getWaitingList_CALLED_웨이팅은_calledAt과_설정의_callGraceMinutes로_graceDeadline을_계산한다() {
    LocalDateTime calledAt = LocalDateTime.now().minusMinutes(1);
    WaitingEntry called = WaitingEntry.restore(UUID.randomUUID(), storeId, "010-1111-0002", 3, 2,
        WaitingStatus.CALLED, LocalDateTime.now().minusMinutes(5),
        LocalDate.now(), calledAt, null, null);

    StoreSettings settings = StoreSettings.restore(UUID.randomUUID(), storeId, 5, 30,
        LocalTime.of(5, 0), null, 10, true, 7); // callGraceMinutes=7

    given(storeRepository.getByOwnerId(ownerId))
        .willReturn(Store.restore(storeId, ownerId, "테스트 매장", "서울", StoreStatus.OPEN, LocalDateTime.now()));
    given(storeSettingsRepository.findByStoreId(storeId)).willReturn(Optional.of(settings));
    given(waitingRepository.findActiveByStoreId(eq(storeId), any(LocalDate.class)))
        .willReturn(List.of(called));

    List<OwnerWaitingResponse> result = service.getWaitingList(ownerId);

    assertThat(result.get(0).graceDeadline()).isEqualTo(calledAt.plusMinutes(7));
  }

  @Test
  void getWaitingList_매장설정_없을때_기본_유예시간으로_graceDeadline을_계산한다() {
    LocalDateTime calledAt = LocalDateTime.now().minusMinutes(1);
    WaitingEntry called = WaitingEntry.restore(UUID.randomUUID(), storeId, "010-1111-0002", 3, 2,
        WaitingStatus.CALLED, LocalDateTime.now().minusMinutes(5),
        LocalDate.now(), calledAt, null, null);

    given(storeRepository.getByOwnerId(ownerId))
        .willReturn(Store.restore(storeId, ownerId, "테스트 매장", "서울", StoreStatus.OPEN, LocalDateTime.now()));
    given(storeSettingsRepository.findByStoreId(storeId)).willReturn(Optional.empty());
    given(waitingRepository.findActiveByStoreId(eq(storeId), any(LocalDate.class)))
        .willReturn(List.of(called));

    List<OwnerWaitingResponse> result = service.getWaitingList(ownerId);

    // WaitingManagementService.DEFAULT_CALL_GRACE_MINUTES == 5
    assertThat(result.get(0).graceDeadline()).isEqualTo(calledAt.plusMinutes(5));
  }

  @Test
  void getWaitingList_활성_대기_없음_빈_목록_반환() {
    given(storeRepository.getByOwnerId(ownerId))
        .willReturn(Store.restore(storeId, ownerId, "테스트 매장", "서울", StoreStatus.OPEN, LocalDateTime.now()));
    given(storeSettingsRepository.findByStoreId(storeId))
        .willReturn(Optional.of(StoreSettings.createDefault(storeId)));
    given(waitingRepository.findActiveByStoreId(eq(storeId), any(LocalDate.class))).willReturn(List.of());

    List<OwnerWaitingResponse> result = service.getWaitingList(ownerId);

    assertThat(result).isEmpty();
  }

  // ===== getDailySummary =====

  @Test
  void getDailySummary_일별_통계_집계() {
    given(storeRepository.getByOwnerId(ownerId))
        .willReturn(Store.restore(storeId, ownerId, "테스트 매장", "서울", StoreStatus.OPEN, LocalDateTime.now()));
    given(storeSettingsRepository.findByStoreId(storeId))
        .willReturn(Optional.of(StoreSettings.createDefault(storeId)));

    given(waitingRepository.countByStatusForStoreAndBusinessDate(eq(storeId), any()))
        .willReturn(Map.of(
            WaitingStatus.WAITING, 3L,
            WaitingStatus.CALLED, 1L,
            WaitingStatus.ENTERED, 5L,
            WaitingStatus.NO_SHOW, 2L,
            WaitingStatus.CANCELLED, 1L));

    DailySummaryResponse response = service.getDailySummary(ownerId);

    assertThat(response.totalRegistered()).isEqualTo(12);
    assertThat(response.totalEntered()).isEqualTo(5);
    assertThat(response.totalNoShow()).isEqualTo(2);
    assertThat(response.totalCancelled()).isEqualTo(1);
    assertThat(response.currentWaiting()).isEqualTo(4);
  }

  @Test
  void getDailySummary_데이터_없을_때_모두_0() {
    given(storeRepository.getByOwnerId(ownerId))
        .willReturn(Store.restore(storeId, ownerId, "테스트 매장", "서울", StoreStatus.OPEN, LocalDateTime.now()));
    given(storeSettingsRepository.findByStoreId(storeId))
        .willReturn(Optional.of(StoreSettings.createDefault(storeId)));
    given(waitingRepository.countByStatusForStoreAndBusinessDate(eq(storeId), any()))
        .willReturn(Map.of());

    DailySummaryResponse response = service.getDailySummary(ownerId);

    assertThat(response.totalRegistered()).isZero();
    assertThat(response.currentWaiting()).isZero();
  }

  // ===== call =====

  @Test
  void call_정상_호출처리() {
    WaitingEntry entry = WaitingEntry.restore(waitingId, storeId, "010-1111-0001", 2, 1, WaitingStatus.WAITING, LocalDateTime.now(),
        LocalDate.now(), null, null, null);
    given(waitingRepository.findById(waitingId)).willReturn(Optional.of(entry));
    given(storeRepository.getByOwnerId(ownerId))
        .willReturn(Store.restore(storeId, ownerId, "홍콩반점", "서울", StoreStatus.OPEN, LocalDateTime.now()));
    given(waitingRepository.save(any())).willReturn(entry);

    service.call(ownerId, waitingId);

    verify(waitingRepository).save(any());
    then(eventPublisher).should().publishEvent(
        new WaitingCalledEvent(storeId, waitingId, "010-1111-0001", 1, "홍콩반점")
    );
  }

  @Test
  void call_소유권_불일치_예외발생() {
    UUID otherStoreId = UUID.randomUUID();
    WaitingEntry entry = WaitingEntry.restore(waitingId, storeId, "010-1111-0001", 2, 1, WaitingStatus.WAITING, LocalDateTime.now(),
        LocalDate.now(), null, null, null);
    given(waitingRepository.findById(waitingId)).willReturn(Optional.of(entry));
    given(storeRepository.getByOwnerId(ownerId))
        .willReturn(Store.restore(otherStoreId, ownerId, "내 매장", "서울", StoreStatus.OPEN, LocalDateTime.now()));

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
    WaitingEntry entry = WaitingEntry.restore(waitingId, storeId, "010-1111-0001", 2, 1, WaitingStatus.CALLED, LocalDateTime.now(),
        LocalDate.now(), null, null, null);
    given(waitingRepository.findById(waitingId)).willReturn(Optional.of(entry));
    given(storeRepository.getByOwnerId(ownerId))
        .willReturn(Store.restore(storeId, ownerId, "테스트 매장", "서울", StoreStatus.OPEN, LocalDateTime.now()));
    given(waitingRepository.save(any())).willReturn(entry);

    service.enter(ownerId, waitingId);

    verify(waitingRepository).save(any());
    then(eventPublisher).should().publishEvent(any(WaitingUpdatedEvent.class));
  }

  @Test
  void enter_소유권_불일치_예외발생() {
    UUID otherStoreId = UUID.randomUUID();
    WaitingEntry entry = WaitingEntry.restore(waitingId, storeId, "010-1111-0001", 2, 1, WaitingStatus.CALLED, LocalDateTime.now(),
        LocalDate.now(), null, null, null);
    given(waitingRepository.findById(waitingId)).willReturn(Optional.of(entry));
    given(storeRepository.getByOwnerId(ownerId))
        .willReturn(Store.restore(otherStoreId, ownerId, "내 매장", "서울", StoreStatus.OPEN, LocalDateTime.now()));

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
    WaitingEntry entry = WaitingEntry.restore(waitingId, storeId, "010-1111-0001", 2, 1, WaitingStatus.CALLED, LocalDateTime.now(),
        LocalDate.now(), null, null, null);
    given(waitingRepository.findById(waitingId)).willReturn(Optional.of(entry));
    given(storeRepository.getByOwnerId(ownerId))
        .willReturn(Store.restore(storeId, ownerId, "테스트 매장", "서울", StoreStatus.OPEN, LocalDateTime.now()));
    given(waitingRepository.save(any())).willReturn(entry);

    service.noShow(ownerId, waitingId);

    verify(waitingRepository).save(any());
    then(eventPublisher).should().publishEvent(any(WaitingUpdatedEvent.class));
  }

  @Test
  void noShow_소유권_불일치_예외발생() {
    UUID otherStoreId = UUID.randomUUID();
    WaitingEntry entry = WaitingEntry.restore(waitingId, storeId, "010-1111-0001", 2, 1, WaitingStatus.CALLED, LocalDateTime.now(),
        LocalDate.now(), null, null, null);
    given(waitingRepository.findById(waitingId)).willReturn(Optional.of(entry));
    given(storeRepository.getByOwnerId(ownerId))
        .willReturn(Store.restore(otherStoreId, ownerId, "내 매장", "서울", StoreStatus.OPEN, LocalDateTime.now()));

    assertThatThrownBy(() -> service.noShow(ownerId, waitingId))
        .isInstanceOf(StoreNotFoundException.class);
  }

  @Test
  void noShow_존재하지않는_대기_예외발생() {
    given(waitingRepository.findById(waitingId)).willReturn(Optional.empty());

    assertThatThrownBy(() -> service.noShow(ownerId, waitingId))
        .isInstanceOf(WaitingNotFoundException.class);
  }

  // ===== postpone =====

  @Test
  void postpone_정상_미루기처리() {
    WaitingEntry entry = WaitingEntry.restore(waitingId, storeId, "010-1111-0001", 2, 1, WaitingStatus.CALLED, LocalDateTime.now(),
        LocalDate.now(), LocalDateTime.now(), null, null);
    given(waitingRepository.findById(waitingId)).willReturn(Optional.of(entry));
    given(storeRepository.getByOwnerId(ownerId))
        .willReturn(Store.restore(storeId, ownerId, "테스트 매장", "서울", StoreStatus.OPEN, LocalDateTime.now()));
    given(waitingRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

    service.postpone(ownerId, waitingId);

    ArgumentCaptor<WaitingEntry> captor = ArgumentCaptor.forClass(WaitingEntry.class);
    verify(waitingRepository).save(captor.capture());
    assertThat(captor.getValue().getStatus()).isEqualTo(WaitingStatus.WAITING);
    assertThat(captor.getValue().getCalledAt()).isNull();
    then(eventPublisher).should().publishEvent(new WaitingPostponedEvent(storeId, waitingId));
  }

  @Test
  void postpone_소유권_불일치_예외발생() {
    UUID otherStoreId = UUID.randomUUID();
    WaitingEntry entry = WaitingEntry.restore(waitingId, storeId, "010-1111-0001", 2, 1, WaitingStatus.CALLED, LocalDateTime.now(),
        LocalDate.now(), LocalDateTime.now(), null, null);
    given(waitingRepository.findById(waitingId)).willReturn(Optional.of(entry));
    given(storeRepository.getByOwnerId(ownerId))
        .willReturn(Store.restore(otherStoreId, ownerId, "내 매장", "서울", StoreStatus.OPEN, LocalDateTime.now()));

    assertThatThrownBy(() -> service.postpone(ownerId, waitingId))
        .isInstanceOf(StoreNotFoundException.class);
  }

  @Test
  void postpone_존재하지않는_대기_예외발생() {
    given(waitingRepository.findById(waitingId)).willReturn(Optional.empty());

    assertThatThrownBy(() -> service.postpone(ownerId, waitingId))
        .isInstanceOf(WaitingNotFoundException.class);
  }

  // ===== getTodayWaitings =====

  @Test
  void getTodayWaitings_오늘_전체_목록_반환() {
    WaitingEntry waiting = WaitingEntry.restore(
        UUID.randomUUID(),
        storeId,
        "010-1111-0001",
        2,
        1,
        WaitingStatus.WAITING,
        LocalDateTime.now(),
        LocalDate.now(), null, null, null
    );
    WaitingEntry entered = WaitingEntry.restore(
        UUID.randomUUID(),
        storeId,
        "010-1111-0002",
        1,
        2,
        WaitingStatus.ENTERED,
        LocalDateTime.now().minusMinutes(30),
        LocalDate.now(), null, null, null
    );

    given(storeRepository.getByOwnerId(ownerId))
        .willReturn(Store.restore(storeId, ownerId, "테스트 매장", "서울", StoreStatus.OPEN, LocalDateTime.now()));
    given(storeSettingsRepository.findByStoreId(storeId))
        .willReturn(Optional.of(StoreSettings.createDefault(storeId)));
    given(waitingRepository.findAllByStoreIdAndBusinessDate(eq(storeId), any(LocalDate.class)))
        .willReturn(List.of(waiting, entered));

    List<TodayWaitingResponse> result = service.getTodayWaitings(ownerId, null);

    assertThat(result).hasSize(2);
    assertThat(result.get(0).phoneNumber()).isEqualTo("****-0001");
    assertThat(result.get(0).status()).isEqualTo(WaitingStatus.WAITING);
    assertThat(result.get(1).phoneNumber()).isEqualTo("****-0002");
    assertThat(result.get(1).status()).isEqualTo(WaitingStatus.ENTERED);
  }

  @Test
  void getTodayWaitings_날짜_지정시_해당_영업일로_조회() {
    LocalDate requestedDate = LocalDate.now().minusDays(3);
    WaitingEntry waiting = WaitingEntry.restore(
        UUID.randomUUID(),
        storeId,
        "010-1111-0003",
        2,
        1,
        WaitingStatus.ENTERED,
        LocalDateTime.now().minusDays(3),
        requestedDate, null, null, null
    );

    given(storeRepository.getByOwnerId(ownerId))
        .willReturn(Store.restore(storeId, ownerId, "테스트 매장", "서울", StoreStatus.OPEN, LocalDateTime.now()));
    given(waitingRepository.findAllByStoreIdAndBusinessDate(storeId, requestedDate))
        .willReturn(List.of(waiting));

    List<TodayWaitingResponse> result = service.getTodayWaitings(ownerId, requestedDate);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).phoneNumber()).isEqualTo("****-0003");
    // 특정 날짜가 주어지면 오늘(currentBusinessDate) 계산을 위한 조회는 필요 없다
    verify(storeSettingsRepository, never()).findByStoreId(any());
  }

  @Test
  void getTodayWaitings_매장_없음_예외발생() {
    given(storeRepository.getByOwnerId(ownerId))
        .willThrow(new StoreNotFoundException("ownerId=" + ownerId));

    assertThatThrownBy(() -> service.getTodayWaitings(ownerId, null))
        .isInstanceOf(StoreNotFoundException.class);
  }
}
