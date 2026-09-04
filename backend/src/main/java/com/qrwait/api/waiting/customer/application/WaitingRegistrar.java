package com.qrwait.api.waiting.customer.application;

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
import java.time.LocalTime;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 등록 1회 시도의 트랜잭션 경계. 채번 경합 재시도는 트랜잭션 밖(WaitingService)에서 이뤄져야
 * 하므로, 트랜잭션 단위를 별도 빈으로 분리한다.
 */
@Component
@RequiredArgsConstructor
public class WaitingRegistrar {

  private static final int DEFAULT_MINUTES_PER_PERSON = 5;
  private static final LocalTime DEFAULT_BUSINESS_DAY_START = LocalTime.of(5, 0);

  private final WaitingRepository waitingRepository;
  private final StoreRepository storeRepository;
  private final StoreSettingsRepository storeSettingsRepository;
  private final ApplicationEventPublisher eventPublisher;

  @Transactional
  public RegisterWaitingResponse registerOnce(UUID storeId, RegisterWaitingRequest request) {
    Store store = storeRepository.findById(storeId)
        .orElseThrow(() -> new StoreNotFoundException(storeId));
    if (store.getStatus() != StoreStatus.OPEN) {
      throw new StoreNotAvailableException(store.getStatus());
    }

    Optional<StoreSettings> settings = storeSettingsRepository.findByStoreId(storeId);
    LocalDateTime now = LocalDateTime.now();
    LocalDate businessDate = settings
        .map(s -> s.businessDateOf(now))
        .orElseGet(() -> defaultBusinessDate(now));

    int waitingNumber = waitingRepository.findNextWaitingNumber(storeId, businessDate);

    WaitingEntry saved = waitingRepository.save(WaitingEntry.create(
        storeId, request.getPhoneNumber(), request.getPartySize(), waitingNumber, businessDate));

    int totalWaiting = waitingRepository.countByStoreIdAndStatus(
        storeId, businessDate, WaitingStatus.WAITING);
    int ahead = totalWaiting - 1;
    int estimatedWaitMinutes = settings
        .map(s -> s.calculateEstimatedWait(ahead))
        .orElse(ahead * DEFAULT_MINUTES_PER_PERSON);

    eventPublisher.publishEvent(new WaitingRegisteredEvent(storeId));

    // 신규 등록자는 대기열 맨 뒤이므로 currentRank == totalWaiting (의도된 동일 값)
    return new RegisterWaitingResponse(
        saved.getId(), waitingNumber, totalWaiting, totalWaiting, estimatedWaitMinutes);
  }

  /** 매장 설정이 없을 때의 영업일 기준. StoreSettings 기본값(05:00)과 같아야 한다. */
  private LocalDate defaultBusinessDate(LocalDateTime at) {
    return at.toLocalTime().isBefore(DEFAULT_BUSINESS_DAY_START)
        ? at.toLocalDate().minusDays(1)
        : at.toLocalDate();
  }
}
