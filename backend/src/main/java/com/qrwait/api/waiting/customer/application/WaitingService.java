package com.qrwait.api.waiting.customer.application;

import com.qrwait.api.store.domain.StoreSettings;
import com.qrwait.api.store.domain.StoreSettingsRepository;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class WaitingService {

  private static final int DEFAULT_MINUTES_PER_PERSON = 5;
  private static final int DEFAULT_CALL_GRACE_MINUTES = 5;
  private static final LocalTime DEFAULT_BUSINESS_DAY_START = LocalTime.of(5, 0);

  /**
   * 채번 경합 재시도 상한. 이 값은 임의의 튜닝 노브가 아니라 "매장당 동시 등록 처리량 상한"이다.
   *
   * 매 라운드마다 살아남은 스레드들이 모두 새 트랜잭션에서 같은 MAX+1을 읽어 같은 번호로
   * 경합하므로 라운드당 하나만 성공한다. 가장 불운한 스레드는 동시 등록자 수만큼의 라운드가
   * 필요하다. 즉 이 상수는 "매장당 동시 등록 상한"과 사실상 같아야 하며, 헤드룸이 거의 없다 —
   * 동시 등록자가 이 값에 가까워지면 정상 요청도 허위로 409(WAITING_NUMBER_CONFLICT)를 받는다.
   * (실측: CONCURRENCY=10 동시성 테스트가 매번 정확히 최악의 경우 — 9+8+...+1=45회 충돌,
   * 마지막 스레드가 15회 중 10번째 시도에서 성공 — 를 때린다. 15는 그 실측에 5회의 실질
   * 여유를 더한 값이다.)
   *
   * 트래픽이 이 상한을 넘어설 필요가 생기면, MAX+1 재시도 방식 자체를 (store, business_date)
   * 카운터 행 + {@code SELECT ... FOR UPDATE}나 단일 {@code INSERT ... SELECT COALESCE(MAX(...),0)+1}
   * 로 교체해야 한다 — 이 숫자를 그냥 올리는 것은 해결책이 아니다.
   */
  private static final int MAX_REGISTER_ATTEMPTS = 15;

  /** 채번 경합으로 인한 유니크 제약 위반만 재시도 대상으로 판별하는 데 쓰는 제약 이름. */
  private static final String WAITING_NUMBER_UNIQUE_CONSTRAINT = "uq_waiting_store_business_date_number";

  private final WaitingRepository waitingRepository;
  private final StoreSettingsRepository storeSettingsRepository;
  private final ApplicationEventPublisher eventPublisher;
  private final WaitingRegistrar waitingRegistrar;

  /**
   * 채번 경합은 유니크 제약 위반으로 드러난다. 위반된 트랜잭션은 rollback-only 이므로
   * 매 시도를 새 트랜잭션으로 돌린다. 우리 채번 제약이 아닌 다른 무결성 위반(예: 매장이
   * 등록 도중 삭제되어 발생하는 FK 위반)은 재시도로 풀리지 않으므로 즉시 재던진다.
   */
  public RegisterWaitingResponse register(UUID storeId, RegisterWaitingRequest request) {
    DataIntegrityViolationException lastConflict = null;
    for (int attempt = 1; attempt <= MAX_REGISTER_ATTEMPTS; attempt++) {
      try {
        return waitingRegistrar.registerOnce(storeId, request);
      } catch (DataIntegrityViolationException e) {
        if (!isWaitingNumberConflict(e)) {
          throw e;
        }
        lastConflict = e;
        log.warn("대기번호 채번 경합으로 재시도합니다. storeId={}, attempt={}/{}",
            storeId, attempt, MAX_REGISTER_ATTEMPTS, e);
      }
    }
    throw new WaitingNumberConflictException(storeId, lastConflict);
  }

  /**
   * 우리 채번 유니크 제약({@value #WAITING_NUMBER_UNIQUE_CONSTRAINT}) 위반인지 판별한다.
   * Hibernate 예외 타입을 애플리케이션 계층으로 끌어오지 않기 위해, Spring의
   * {@link DataIntegrityViolationException#getMostSpecificCause()} 메시지에 제약 이름이
   * 포함되는지로만 판단한다.
   */
  private boolean isWaitingNumberConflict(DataIntegrityViolationException e) {
    String message = e.getMostSpecificCause().getMessage();
    return message != null && message.contains(WAITING_NUMBER_UNIQUE_CONSTRAINT);
  }

  @Transactional(readOnly = true)
  public MyWaitingStatusResponse getStatus(UUID waitingId) {
    WaitingEntry entry = waitingRepository.findById(waitingId)
        .orElseThrow(() -> new WaitingNotFoundException(waitingId));

    if (entry.getStatus() != WaitingStatus.WAITING && entry.getStatus() != WaitingStatus.CALLED) {
      throw new WaitingNotFoundException(waitingId);
    }

    List<WaitingEntry> waitingList = waitingRepository
        .findByStoreIdAndStatus(entry.getStoreId(), entry.getBusinessDate(), WaitingStatus.WAITING);

    long ahead = waitingList.stream()
        .filter(e -> e.getWaitingNumber() < entry.getWaitingNumber())
        .count();

    int currentRank = (int) ahead + 1;
    int totalWaiting = waitingList.size();

    Optional<StoreSettings> settings = storeSettingsRepository.findByStoreId(entry.getStoreId());
    int estimatedWaitMinutes = estimatedWaitMinutes(settings, (int) ahead);

    int graceMinutes = settings
        .map(StoreSettings::getCallGraceMinutes)
        .orElse(DEFAULT_CALL_GRACE_MINUTES);
    LocalDateTime graceDeadline = entry.getCalledAt() == null
        ? null
        : entry.getCalledAt().plusMinutes(graceMinutes);

    return new MyWaitingStatusResponse(
        currentRank, totalWaiting, estimatedWaitMinutes, entry.getStatus(), graceDeadline);
  }

  @Transactional
  public void cancel(UUID waitingId) {
    WaitingEntry entry = waitingRepository.findById(waitingId)
        .orElseThrow(() -> new WaitingNotFoundException(waitingId));

    WaitingEntry cancelled = entry.cancel();
    waitingRepository.save(cancelled);

    eventPublisher.publishEvent(new WaitingUpdatedEvent(cancelled.getStoreId()));
  }

  @Transactional(readOnly = true)
  public WaitingStatusResponse getStoreWaitingStatus(UUID storeId) {
    Optional<StoreSettings> settings = storeSettingsRepository.findByStoreId(storeId);
    LocalDate businessDate = settings
        .map(s -> s.businessDateOf(LocalDateTime.now()))
        .orElseGet(() -> defaultBusinessDate(LocalDateTime.now()));

    int totalWaiting = waitingRepository.countByStoreIdAndStatus(storeId, businessDate, WaitingStatus.WAITING);
    int estimatedWaitMinutes = estimatedWaitMinutes(settings, totalWaiting);
    // 매장 전체 상태 조회는 특정 손님이 없으므로 currentRank 자리에 totalWaiting을 그대로 둔다 (의도)
    return new WaitingStatusResponse(totalWaiting, totalWaiting, estimatedWaitMinutes);
  }

  private int estimatedWaitMinutes(Optional<StoreSettings> settings, int ahead) {
    return settings
        .map(s -> s.calculateEstimatedWait(ahead))
        .orElse(ahead * DEFAULT_MINUTES_PER_PERSON);
  }

  /** 매장 설정이 없을 때의 영업일 기준. StoreSettings 기본값(05:00)과 같아야 한다. */
  private LocalDate defaultBusinessDate(LocalDateTime at) {
    return at.toLocalTime().isBefore(DEFAULT_BUSINESS_DAY_START)
        ? at.toLocalDate().minusDays(1)
        : at.toLocalDate();
  }
}
