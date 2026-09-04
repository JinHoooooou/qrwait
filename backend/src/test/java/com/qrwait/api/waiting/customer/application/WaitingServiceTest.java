package com.qrwait.api.waiting.customer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.qrwait.api.store.domain.StoreNotAvailableException;
import com.qrwait.api.store.domain.StoreNotFoundException;
import com.qrwait.api.store.domain.StoreSettings;
import com.qrwait.api.store.domain.StoreSettingsRepository;
import com.qrwait.api.store.domain.StoreStatus;
import com.qrwait.api.waiting.customer.dto.MyWaitingStatusResponse;
import com.qrwait.api.waiting.customer.dto.RegisterWaitingRequest;
import com.qrwait.api.waiting.customer.dto.RegisterWaitingResponse;
import com.qrwait.api.waiting.customer.dto.WaitingStatusResponse;
import com.qrwait.api.waiting.domain.WaitingEntry;
import com.qrwait.api.waiting.domain.WaitingNotFoundException;
import com.qrwait.api.waiting.domain.WaitingNumberConflictException;
import com.qrwait.api.waiting.domain.WaitingRepository;
import com.qrwait.api.waiting.domain.WaitingStatus;
import com.qrwait.api.waiting.domain.event.WaitingUpdatedEvent;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
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
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class WaitingServiceTest {

  @Mock
  WaitingRepository waitingRepository;
  @Mock
  StoreSettingsRepository storeSettingsRepository;
  @Mock
  ApplicationEventPublisher eventPublisher;
  @Mock
  WaitingRegistrar waitingRegistrar;

  WaitingService waitingService;

  @BeforeEach
  void setUp() {
    waitingService =
        new WaitingService(waitingRepository, storeSettingsRepository, eventPublisher, waitingRegistrar);
  }

  // ===== register =====
  // register의 등록 본문(매장 조회·영업일 확정·채번·예상대기시간 계산)은 WaitingRegistrar가 담당한다.
  // 여기서는 WaitingService.register가 WaitingRegistrar에 위임하고, 채번 경합(유니크 제약 위반)에
  // 한해서만 트랜잭션 밖에서 재시도하는지를 검증한다.

  @Test
  void register_정상등록시_레지스트라에_위임한다() {
    UUID storeId = UUID.randomUUID();
    RegisterWaitingRequest request = new RegisterWaitingRequest();
    request.setPhoneNumber("010-1234-5678");
    request.setPartySize(2);

    RegisterWaitingResponse expected = new RegisterWaitingResponse(UUID.randomUUID(), 3, 2, 2, 10);
    given(waitingRegistrar.registerOnce(storeId, request)).willReturn(expected);

    RegisterWaitingResponse response = waitingService.register(storeId, request);

    assertThat(response).isEqualTo(expected);
    then(waitingRegistrar).should(times(1)).registerOnce(storeId, request);
  }

  @Test
  void register_채번_경합시_새_트랜잭션으로_재시도해_성공한다() {
    UUID storeId = UUID.randomUUID();
    RegisterWaitingRequest request = new RegisterWaitingRequest();
    request.setPhoneNumber("010-1234-5678");
    request.setPartySize(2);

    RegisterWaitingResponse expected = new RegisterWaitingResponse(UUID.randomUUID(), 1, 1, 1, 5);
    willThrow(waitingNumberConflict())
        .willReturn(expected)
        .given(waitingRegistrar).registerOnce(storeId, request);

    RegisterWaitingResponse response = waitingService.register(storeId, request);

    assertThat(response).isEqualTo(expected);
    then(waitingRegistrar).should(times(2)).registerOnce(storeId, request);
  }

  @Test
  void register_재시도가_모두_실패하면_원인을_cause로_담아_WaitingNumberConflictException을_던진다() {
    UUID storeId = UUID.randomUUID();
    RegisterWaitingRequest request = new RegisterWaitingRequest();
    request.setPhoneNumber("010-1234-5678");
    request.setPartySize(2);

    willThrow(waitingNumberConflict())
        .given(waitingRegistrar).registerOnce(storeId, request);

    assertThatThrownBy(() -> waitingService.register(storeId, request))
        .isInstanceOf(WaitingNumberConflictException.class)
        .hasCauseInstanceOf(DataIntegrityViolationException.class);
    then(waitingRegistrar).should(times(15)).registerOnce(storeId, request);
  }

  @Test
  void register_우리_채번_제약이_아닌_무결성_위반은_재시도없이_즉시_전파된다() {
    UUID storeId = UUID.randomUUID();
    RegisterWaitingRequest request = new RegisterWaitingRequest();
    request.setPhoneNumber("010-1234-5678");
    request.setPartySize(2);

    // 예: 등록 도중 매장이 삭제되어 발생하는 FK 위반. 재시도로 풀리지 않으므로 즉시 전파돼야 한다.
    DataIntegrityViolationException fkViolation =
        new DataIntegrityViolationException("insert or update on table \"waitings\" violates "
            + "foreign key constraint \"waitings_store_id_fkey\"");
    willThrow(fkViolation).given(waitingRegistrar).registerOnce(storeId, request);

    assertThatThrownBy(() -> waitingService.register(storeId, request))
        .isSameAs(fkViolation);
    then(waitingRegistrar).should(times(1)).registerOnce(storeId, request);
  }

  /** 우리 채번 유니크 제약 위반과 동일한 형태의 예외 — WaitingService가 재시도 대상으로 판별해야 한다. */
  private static DataIntegrityViolationException waitingNumberConflict() {
    return new DataIntegrityViolationException("duplicate key value violates unique constraint "
        + "\"uq_waiting_store_business_date_number\"");
  }

  @Test
  void register_존재하지않는_storeId_예외발생() {
    UUID storeId = UUID.randomUUID();
    RegisterWaitingRequest request = new RegisterWaitingRequest();
    request.setPhoneNumber("010-1234-5678");
    request.setPartySize(2);

    given(waitingRegistrar.registerOnce(storeId, request)).willThrow(new StoreNotFoundException(storeId));

    assertThatThrownBy(() -> waitingService.register(storeId, request))
        .isInstanceOf(StoreNotFoundException.class);
    then(waitingRegistrar).should(times(1)).registerOnce(storeId, request);
  }

  @Test
  void register_매장_OPEN아닐때_예외발생() {
    UUID storeId = UUID.randomUUID();
    RegisterWaitingRequest request = new RegisterWaitingRequest();
    request.setPhoneNumber("010-1234-5678");
    request.setPartySize(2);

    given(waitingRegistrar.registerOnce(storeId, request))
        .willThrow(new StoreNotAvailableException(StoreStatus.CLOSED));

    assertThatThrownBy(() -> waitingService.register(storeId, request))
        .isInstanceOf(StoreNotAvailableException.class);
    then(waitingRegistrar).should(times(1)).registerOnce(storeId, request);
  }

  // ===== getStoreWaitingStatus =====

  @Test
  void getStoreWaitingStatus_StoreSettings_기반_예상대기시간_계산() {
    UUID storeId = UUID.randomUUID();
    given(waitingRepository.countByStoreIdAndStatus(eq(storeId), any(LocalDate.class), eq(WaitingStatus.WAITING)))
        .willReturn(4);
    given(storeSettingsRepository.findByStoreId(storeId))
        .willReturn(Optional.of(StoreSettings.createDefault(storeId))); // 30/5*4=24

    WaitingStatusResponse response = waitingService.getStoreWaitingStatus(storeId);

    assertThat(response.totalWaiting()).isEqualTo(4);
    assertThat(response.estimatedWaitMinutes()).isEqualTo(24);
  }

  @Test
  void getStoreWaitingStatus_StoreSettings_없을때_fallback_계산() {
    UUID storeId = UUID.randomUUID();
    given(waitingRepository.countByStoreIdAndStatus(eq(storeId), any(LocalDate.class), eq(WaitingStatus.WAITING)))
        .willReturn(4);
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
        waitingId, storeId, "010-1234-5678", 2, 3, WaitingStatus.WAITING, LocalDateTime.now(),
        LocalDate.now(), null, null, null);

    List<WaitingEntry> waitingList = List.of(
        WaitingEntry.restore(UUID.randomUUID(), storeId, "010-1111-0001", 2, 1, WaitingStatus.WAITING, LocalDateTime.now(),
            LocalDate.now(), null, null, null),
        WaitingEntry.restore(UUID.randomUUID(), storeId, "010-1111-0002", 2, 2, WaitingStatus.WAITING, LocalDateTime.now(),
            LocalDate.now(), null, null, null),
        target
    );

    given(waitingRepository.findById(waitingId)).willReturn(Optional.of(target));
    given(waitingRepository.findByStoreIdAndStatus(storeId, target.getBusinessDate(), WaitingStatus.WAITING))
        .willReturn(waitingList);

    MyWaitingStatusResponse response = waitingService.getStatus(waitingId);

    assertThat(response.currentRank()).isEqualTo(3);
    assertThat(response.totalWaiting()).isEqualTo(3);
    assertThat(response.estimatedWaitMinutes()).isEqualTo(10); // 앞 2팀 × 5분 (fallback)
    assertThat(response.status()).isEqualTo(WaitingStatus.WAITING);
  }

  @Test
  void getStatus_호출된_웨이팅은_CALLED_상태를_반환() {
    UUID storeId = UUID.randomUUID();
    UUID waitingId = UUID.randomUUID();

    WaitingEntry called = WaitingEntry.restore(
        waitingId, storeId, "010-1234-5678", 2, 1, WaitingStatus.CALLED, LocalDateTime.now(),
        LocalDate.now(), null, null, null);

    given(waitingRepository.findById(waitingId)).willReturn(Optional.of(called));
    given(waitingRepository.findByStoreIdAndStatus(storeId, called.getBusinessDate(), WaitingStatus.WAITING))
        .willReturn(List.of());

    MyWaitingStatusResponse response = waitingService.getStatus(waitingId);

    assertThat(response.status()).isEqualTo(WaitingStatus.CALLED);
  }

  @Test
  void getStatus_CALLED_상태면_calledAt과_설정의_callGraceMinutes로_graceDeadline을_계산한다() {
    UUID storeId = UUID.randomUUID();
    UUID waitingId = UUID.randomUUID();
    LocalDateTime calledAt = LocalDateTime.now().minusMinutes(1);

    WaitingEntry called = WaitingEntry.restore(
        waitingId, storeId, "010-1234-5678", 2, 1, WaitingStatus.CALLED, LocalDateTime.now(),
        LocalDate.now(), calledAt, null, null);

    StoreSettings settings = StoreSettings.restore(UUID.randomUUID(), storeId, 5, 30,
        null, null, 10, true, LocalTime.of(5, 0), 7); // callGraceMinutes=7

    given(waitingRepository.findById(waitingId)).willReturn(Optional.of(called));
    given(waitingRepository.findByStoreIdAndStatus(storeId, called.getBusinessDate(), WaitingStatus.WAITING))
        .willReturn(List.of());
    given(storeSettingsRepository.findByStoreId(storeId)).willReturn(Optional.of(settings));

    MyWaitingStatusResponse response = waitingService.getStatus(waitingId);

    assertThat(response.graceDeadline()).isEqualTo(calledAt.plusMinutes(7));
  }

  @Test
  void getStatus_WAITING_상태면_calledAt이_없으므로_graceDeadline은_null() {
    UUID storeId = UUID.randomUUID();
    UUID waitingId = UUID.randomUUID();

    WaitingEntry waiting = WaitingEntry.restore(
        waitingId, storeId, "010-1234-5678", 2, 1, WaitingStatus.WAITING, LocalDateTime.now(),
        LocalDate.now(), null, null, null);

    given(waitingRepository.findById(waitingId)).willReturn(Optional.of(waiting));
    given(waitingRepository.findByStoreIdAndStatus(storeId, waiting.getBusinessDate(), WaitingStatus.WAITING))
        .willReturn(List.of(waiting));

    MyWaitingStatusResponse response = waitingService.getStatus(waitingId);

    assertThat(response.graceDeadline()).isNull();
  }

  @Test
  void getStatus_매장설정_없을때_기본_유예시간으로_graceDeadline을_계산한다() {
    UUID storeId = UUID.randomUUID();
    UUID waitingId = UUID.randomUUID();
    LocalDateTime calledAt = LocalDateTime.now().minusMinutes(1);

    WaitingEntry called = WaitingEntry.restore(
        waitingId, storeId, "010-1234-5678", 2, 1, WaitingStatus.CALLED, LocalDateTime.now(),
        LocalDate.now(), calledAt, null, null);

    given(waitingRepository.findById(waitingId)).willReturn(Optional.of(called));
    given(waitingRepository.findByStoreIdAndStatus(storeId, called.getBusinessDate(), WaitingStatus.WAITING))
        .willReturn(List.of());
    given(storeSettingsRepository.findByStoreId(storeId)).willReturn(Optional.empty());

    MyWaitingStatusResponse response = waitingService.getStatus(waitingId);

    // WaitingService.DEFAULT_CALL_GRACE_MINUTES == 5
    assertThat(response.graceDeadline()).isEqualTo(calledAt.plusMinutes(5));
  }

  @Test
  void getStatus_취소된_웨이팅_조회시_예외발생() {
    UUID waitingId = UUID.randomUUID();
    WaitingEntry cancelled = WaitingEntry.restore(
        waitingId, UUID.randomUUID(), "010-9999-9999", 2, 1, WaitingStatus.CANCELLED, LocalDateTime.now(),
        LocalDate.now(), null, null, null);

    given(waitingRepository.findById(waitingId)).willReturn(Optional.of(cancelled));

    assertThatThrownBy(() -> waitingService.getStatus(waitingId))
        .isInstanceOf(WaitingNotFoundException.class);
  }

  // ===== cancel =====

  @Test
  void cancel_정상취소_상태가_CANCELLED로_변경됨() {
    UUID waitingId = UUID.randomUUID();
    WaitingEntry entry = WaitingEntry.restore(
        waitingId, UUID.randomUUID(), "010-9999-9999", 2, 1, WaitingStatus.WAITING, LocalDateTime.now(),
        LocalDate.now(), null, null, null);

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
        waitingId, UUID.randomUUID(), "010-9999-9999", 2, 1, WaitingStatus.CANCELLED, LocalDateTime.now(),
        LocalDate.now(), null, null, null);

    given(waitingRepository.findById(waitingId)).willReturn(Optional.of(entry));

    assertThatThrownBy(() -> waitingService.cancel(waitingId))
        .isInstanceOf(IllegalStateException.class);
  }
}
