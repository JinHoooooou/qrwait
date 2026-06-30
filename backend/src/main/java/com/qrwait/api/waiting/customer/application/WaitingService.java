package com.qrwait.api.waiting.customer.application;

import com.qrwait.api.store.domain.Store;
import com.qrwait.api.store.domain.StoreNotAvailableException;
import com.qrwait.api.store.domain.StoreNotFoundException;
import com.qrwait.api.store.domain.StoreRepository;
import com.qrwait.api.store.domain.StoreSettingsRepository;
import com.qrwait.api.store.domain.StoreStatus;
import com.qrwait.api.waiting.customer.dto.MyWaitingStatusResponse;
import com.qrwait.api.waiting.customer.dto.RegisterWaitingRequest;
import com.qrwait.api.waiting.customer.dto.RegisterWaitingResponse;
import com.qrwait.api.waiting.customer.dto.WaitingStatusResponse;
import com.qrwait.api.waiting.domain.WaitingEntry;
import com.qrwait.api.waiting.domain.WaitingNotFoundException;
import com.qrwait.api.waiting.domain.WaitingRepository;
import com.qrwait.api.waiting.domain.WaitingStatus;
import com.qrwait.api.waiting.domain.event.WaitingRegisteredEvent;
import com.qrwait.api.waiting.domain.event.WaitingUpdatedEvent;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WaitingService {

  private static final int DEFAULT_MINUTES_PER_PERSON = 5;

  private final WaitingRepository waitingRepository;
  private final StoreRepository storeRepository;
  private final StoreSettingsRepository storeSettingsRepository;
  private final ApplicationEventPublisher eventPublisher;

  @Transactional
  public RegisterWaitingResponse register(UUID storeId, RegisterWaitingRequest request) {
    Store store = storeRepository.findById(storeId)
        .orElseThrow(() -> new StoreNotFoundException(storeId));
    if (store.getStatus() != StoreStatus.OPEN) {
      throw new StoreNotAvailableException(store.getStatus());
    }

    int waitingNumber = waitingRepository.findNextWaitingNumber(storeId);

    WaitingEntry entry = WaitingEntry.create(
        storeId, request.getPhoneNumber(), request.getPartySize(), waitingNumber);
    WaitingEntry saved = waitingRepository.save(entry);

    int totalWaiting = waitingRepository.countByStoreIdAndStatus(storeId, WaitingStatus.WAITING);
    int estimatedWaitMinutes = estimatedWaitMinutes(storeId, totalWaiting);

    eventPublisher.publishEvent(new WaitingRegisteredEvent(storeId));

    // 신규 등록자는 대기열 맨 뒤이므로 currentRank == totalWaiting (의도된 동일 값)
    return new RegisterWaitingResponse(
        saved.getId(),
        waitingNumber,
        totalWaiting,
        totalWaiting,
        estimatedWaitMinutes
    );
  }

  @Transactional(readOnly = true)
  public MyWaitingStatusResponse getStatus(UUID waitingId) {
    WaitingEntry entry = waitingRepository.findById(waitingId)
        .orElseThrow(() -> new WaitingNotFoundException(waitingId));

    if (entry.getStatus() != WaitingStatus.WAITING && entry.getStatus() != WaitingStatus.CALLED) {
      throw new WaitingNotFoundException(waitingId);
    }

    List<WaitingEntry> waitingList = waitingRepository
        .findByStoreIdAndStatus(entry.getStoreId(), WaitingStatus.WAITING);

    long ahead = waitingList.stream()
        .filter(e -> e.getWaitingNumber() < entry.getWaitingNumber())
        .count();

    int currentRank = (int) ahead + 1;
    int totalWaiting = waitingList.size();

    int estimatedWaitMinutes = estimatedWaitMinutes(entry.getStoreId(), (int) ahead);

    return new MyWaitingStatusResponse(currentRank, totalWaiting, estimatedWaitMinutes, entry.getStatus());
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
    int totalWaiting = waitingRepository.countByStoreIdAndStatus(storeId, WaitingStatus.WAITING);
    int estimatedWaitMinutes = estimatedWaitMinutes(storeId, totalWaiting);
    // 매장 전체 상태 조회는 특정 손님이 없으므로 currentRank 자리에 totalWaiting을 그대로 둔다 (의도)
    return new WaitingStatusResponse(totalWaiting, totalWaiting, estimatedWaitMinutes);
  }

  private int estimatedWaitMinutes(UUID storeId, int ahead) {
    return storeSettingsRepository.findByStoreId(storeId)
        .map(settings -> settings.calculateEstimatedWait(ahead))
        .orElse(ahead * DEFAULT_MINUTES_PER_PERSON);
  }
}
