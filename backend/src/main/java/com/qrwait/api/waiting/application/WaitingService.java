package com.qrwait.api.waiting.application;

import com.qrwait.api.store.domain.Store;
import com.qrwait.api.store.domain.StoreNotAvailableException;
import com.qrwait.api.store.domain.StoreNotFoundException;
import com.qrwait.api.store.domain.StoreRepository;
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
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WaitingService {

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
        storeId, request.getVisitorName(), request.getPartySize(), waitingNumber);
    WaitingEntry saved = waitingRepository.save(entry);

    int totalWaiting = waitingRepository.countByStoreIdAndStatus(storeId, WaitingStatus.WAITING);
    int estimatedWaitMinutes = storeSettingsRepository.findByStoreId(storeId)
        .map(settings -> settings.calculateEstimatedWait(totalWaiting))
        .orElse(totalWaiting * 5);

    String waitingToken = UUID.randomUUID().toString();

    eventPublisher.publishEvent(new WaitingRegisteredEvent(storeId));

    return new RegisterWaitingResponse(
        saved.getId(),
        waitingNumber,
        totalWaiting,
        totalWaiting,
        estimatedWaitMinutes,
        waitingToken
    );
  }

  @Transactional(readOnly = true)
  public WaitingStatusResponse getStatus(UUID waitingId) {
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

    int estimatedWaitMinutes = storeSettingsRepository.findByStoreId(entry.getStoreId())
        .map(settings -> settings.calculateEstimatedWait((int) ahead))
        .orElse((int) ahead * 5);

    return new WaitingStatusResponse(currentRank, totalWaiting, estimatedWaitMinutes);
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
    int estimatedWaitMinutes = storeSettingsRepository.findByStoreId(storeId)
        .map(settings -> settings.calculateEstimatedWait(totalWaiting))
        .orElse(totalWaiting * 5);
    return new WaitingStatusResponse(totalWaiting, totalWaiting, estimatedWaitMinutes);
  }
}
