package com.qrwait.api.waiting.application;

import com.qrwait.api.shared.sse.SsePublisher;
import com.qrwait.api.store.domain.StoreNotFoundException;
import com.qrwait.api.store.domain.StoreRepository;
import com.qrwait.api.waiting.application.dto.DailySummaryResponse;
import com.qrwait.api.waiting.application.dto.OwnerWaitingResponse;
import com.qrwait.api.waiting.application.dto.TodayWaitingResponse;
import com.qrwait.api.waiting.domain.WaitingEntry;
import com.qrwait.api.waiting.domain.WaitingNotFoundException;
import com.qrwait.api.waiting.domain.WaitingRepository;
import com.qrwait.api.waiting.domain.WaitingStatus;
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
    UUID storeId = resolveStoreId(ownerId);
    return waitingRepository.findActiveByStoreId(storeId).stream()
        .map(this::toOwnerWaitingResponse)
        .toList();
  }

  @Transactional(readOnly = true)
  public DailySummaryResponse getDailySummary(UUID ownerId) {
    UUID storeId = resolveStoreId(ownerId);
    LocalDate today = LocalDate.now();

    long totalRegistered = waitingRepository.countByStoreIdAndStatusAndDate(storeId, WaitingStatus.WAITING, today)
        + waitingRepository.countByStoreIdAndStatusAndDate(storeId, WaitingStatus.CALLED, today)
        + waitingRepository.countByStoreIdAndStatusAndDate(storeId, WaitingStatus.ENTERED, today)
        + waitingRepository.countByStoreIdAndStatusAndDate(storeId, WaitingStatus.NO_SHOW, today)
        + waitingRepository.countByStoreIdAndStatusAndDate(storeId, WaitingStatus.CANCELLED, today);

    long totalEntered = waitingRepository.countByStoreIdAndStatusAndDate(storeId, WaitingStatus.ENTERED, today);
    long totalNoShow = waitingRepository.countByStoreIdAndStatusAndDate(storeId, WaitingStatus.NO_SHOW, today);
    long totalCancelled = waitingRepository.countByStoreIdAndStatusAndDate(storeId, WaitingStatus.CANCELLED, today);
    long currentWaiting = waitingRepository.countByStoreIdAndStatusAndDate(storeId, WaitingStatus.WAITING, today)
        + waitingRepository.countByStoreIdAndStatusAndDate(storeId, WaitingStatus.CALLED, today);

    return new DailySummaryResponse(totalRegistered, totalEntered, totalNoShow, totalCancelled, currentWaiting);
  }

  @Transactional
  public void call(UUID ownerId, UUID waitingId) {
    WaitingEntry entry = waitingRepository.findById(waitingId)
        .orElseThrow(() -> new WaitingNotFoundException(waitingId));

    UUID ownerStoreId = resolveStoreId(ownerId);
    if (!ownerStoreId.equals(entry.getStoreId())) {
      throw new StoreNotFoundException("ownerId=" + ownerId);
    }

    WaitingEntry called = entry.call();
    waitingRepository.save(called);

    eventPublisher.publishEvent(new WaitingCalledEvent(called.getStoreId(), waitingId));
  }

  @Transactional
  public void enter(UUID ownerId, UUID waitingId) {
    WaitingEntry entry = waitingRepository.findById(waitingId)
        .orElseThrow(() -> new WaitingNotFoundException(waitingId));

    UUID ownerStoreId = resolveStoreId(ownerId);
    if (!ownerStoreId.equals(entry.getStoreId())) {
      throw new StoreNotFoundException("ownerId=" + ownerId);
    }

    WaitingEntry entered = entry.enter();
    waitingRepository.save(entered);

    eventPublisher.publishEvent(new WaitingUpdatedEvent(entered.getStoreId()));
  }

  @Transactional
  public void noShow(UUID ownerId, UUID waitingId) {
    WaitingEntry entry = waitingRepository.findById(waitingId)
        .orElseThrow(() -> new WaitingNotFoundException(waitingId));

    UUID ownerStoreId = resolveStoreId(ownerId);
    if (!ownerStoreId.equals(entry.getStoreId())) {
      throw new StoreNotFoundException("ownerId=" + ownerId);
    }

    WaitingEntry noShowed = entry.noShow();
    waitingRepository.save(noShowed);

    eventPublisher.publishEvent(new WaitingUpdatedEvent(noShowed.getStoreId()));
  }

  @Transactional(readOnly = true)
  public List<TodayWaitingResponse> getTodayWaitings(UUID ownerId) {
    UUID storeId = resolveStoreId(ownerId);
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
    UUID storeId = resolveStoreId(ownerId);
    return ssePublisher.subscribeOwner(storeId);
  }

  private UUID resolveStoreId(UUID ownerId) {
    return storeRepository.findByOwnerId(ownerId)
        .orElseThrow(() -> new StoreNotFoundException("ownerId=" + ownerId))
        .getId();
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
}
