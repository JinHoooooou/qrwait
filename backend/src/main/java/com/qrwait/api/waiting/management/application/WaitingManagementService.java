package com.qrwait.api.waiting.management.application;

import com.qrwait.api.shared.sse.SsePublisher;
import com.qrwait.api.store.domain.Store;
import com.qrwait.api.store.domain.StoreNotFoundException;
import com.qrwait.api.store.domain.StoreRepository;
import com.qrwait.api.waiting.management.dto.DailySummaryResponse;
import com.qrwait.api.waiting.management.dto.OwnerWaitingResponse;
import com.qrwait.api.waiting.management.dto.TodayWaitingResponse;
import com.qrwait.api.waiting.domain.DailySummary;
import com.qrwait.api.waiting.domain.WaitingEntry;
import com.qrwait.api.waiting.domain.WaitingNotFoundException;
import com.qrwait.api.waiting.domain.WaitingRepository;
import com.qrwait.api.waiting.domain.event.WaitingCalledEvent;
import com.qrwait.api.waiting.domain.event.WaitingUpdatedEvent;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
@RequiredArgsConstructor
public class WaitingManagementService {

  private final WaitingRepository waitingRepository;
  private final StoreRepository storeRepository;
  private final SsePublisher ssePublisher;
  private final ApplicationEventPublisher eventPublisher;

  @Transactional(readOnly = true)
  public List<OwnerWaitingResponse> getWaitingList(UUID ownerId) {
    UUID storeId = storeRepository.getByOwnerId(ownerId).getId();
    return waitingRepository.findActiveByStoreId(storeId).stream()
        .map(this::toOwnerWaitingResponse)
        .toList();
  }

  @Transactional(readOnly = true)
  public DailySummaryResponse getDailySummary(UUID ownerId) {
    UUID storeId = storeRepository.getByOwnerId(ownerId).getId();
    DailySummary summary = DailySummary.from(
        waitingRepository.countByStatusForStoreAndDate(storeId, LocalDate.now())
    );
    return DailySummaryResponse.from(summary);
  }

  @Transactional
  public void call(UUID ownerId, UUID waitingId) {
    OwnedEntry owned = loadOwnedEntry(ownerId, waitingId);
    WaitingEntry called = owned.entry().call();
    waitingRepository.save(called);
    eventPublisher.publishEvent(new WaitingCalledEvent(
        called.getStoreId(),
        waitingId,
        called.getPhoneNumber(),
        called.getWaitingNumber(),
        owned.store().getName()
    ));
  }

  @Transactional
  public void enter(UUID ownerId, UUID waitingId) {
    WaitingEntry entered = loadOwnedEntry(ownerId, waitingId).entry().enter();
    waitingRepository.save(entered);
    eventPublisher.publishEvent(new WaitingUpdatedEvent(entered.getStoreId()));
  }

  @Transactional
  public void noShow(UUID ownerId, UUID waitingId) {
    WaitingEntry noShowed = loadOwnedEntry(ownerId, waitingId).entry().noShow();
    waitingRepository.save(noShowed);
    eventPublisher.publishEvent(new WaitingUpdatedEvent(noShowed.getStoreId()));
  }

  @Transactional(readOnly = true)
  public List<TodayWaitingResponse> getTodayWaitings(UUID ownerId) {
    UUID storeId = storeRepository.getByOwnerId(ownerId).getId();
    return waitingRepository.findAllByStoreIdAndDate(storeId, LocalDate.now())
        .stream()
        .map(entry -> new TodayWaitingResponse(
            entry.getId(),
            entry.getWaitingNumber(),
            entry.getPhoneNumber(),
            entry.getPartySize(),
            entry.getStatus(),
            entry.getCreatedAt()
        ))
        .toList();
  }

  public SseEmitter subscribeOwnerDashboard(UUID ownerId) {
    UUID storeId = storeRepository.getByOwnerId(ownerId).getId();
    return ssePublisher.subscribeOwner(storeId);
  }

  private OwnerWaitingResponse toOwnerWaitingResponse(WaitingEntry entry) {
    long elapsedMinutes = ChronoUnit.MINUTES.between(entry.getCreatedAt(), LocalDateTime.now());
    return new OwnerWaitingResponse(
        entry.getId(),
        entry.getWaitingNumber(),
        entry.getPhoneNumber(),
        entry.getPartySize(),
        entry.getStatus(),
        elapsedMinutes
    );
  }

  private OwnedEntry loadOwnedEntry(UUID ownerId, UUID waitingId) {
    WaitingEntry entry = waitingRepository.findById(waitingId)
        .orElseThrow(() -> new WaitingNotFoundException(waitingId));
    Store store = storeRepository.getByOwnerId(ownerId);
    if (!entry.belongsTo(store.getId())) {
      throw new StoreNotFoundException("ownerId=" + ownerId);
    }
    return new OwnedEntry(store, entry);
  }

  private record OwnedEntry(Store store, WaitingEntry entry) {

  }
}
