package com.qrwait.api.waiting.management.application;

import com.qrwait.api.shared.privacy.PhoneNumberMasker;
import com.qrwait.api.shared.sse.SsePublisher;
import com.qrwait.api.store.domain.Store;
import com.qrwait.api.store.domain.StoreNotFoundException;
import com.qrwait.api.store.domain.StoreRepository;
import com.qrwait.api.store.domain.StoreSettingsRepository;
import com.qrwait.api.waiting.management.dto.DailySummaryResponse;
import com.qrwait.api.waiting.management.dto.OwnerWaitingResponse;
import com.qrwait.api.waiting.management.dto.TodayWaitingResponse;
import com.qrwait.api.waiting.domain.DailySummary;
import com.qrwait.api.waiting.domain.WaitingEntry;
import com.qrwait.api.waiting.domain.WaitingNotFoundException;
import com.qrwait.api.waiting.domain.WaitingRepository;
import com.qrwait.api.store.domain.StoreSettings;
import com.qrwait.api.waiting.domain.event.WaitingCalledEvent;
import com.qrwait.api.waiting.domain.event.WaitingPostponedEvent;
import com.qrwait.api.waiting.domain.event.WaitingUpdatedEvent;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
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

  private static final LocalTime DEFAULT_BUSINESS_DAY_START = LocalTime.of(5, 0);
  private static final int DEFAULT_CALL_GRACE_MINUTES = 5;

  private final WaitingRepository waitingRepository;
  private final StoreRepository storeRepository;
  private final StoreSettingsRepository storeSettingsRepository;
  private final SsePublisher ssePublisher;
  private final ApplicationEventPublisher eventPublisher;

  @Transactional(readOnly = true)
  public List<OwnerWaitingResponse> getWaitingList(UUID ownerId) {
    UUID storeId = storeRepository.getByOwnerId(ownerId).getId();
    int graceMinutes = storeSettingsRepository.findByStoreId(storeId)
        .map(StoreSettings::getCallGraceMinutes)
        .orElse(DEFAULT_CALL_GRACE_MINUTES);
    return waitingRepository.findActiveByStoreId(storeId, currentBusinessDate(storeId)).stream()
        .map(entry -> toOwnerWaitingResponse(entry, graceMinutes))
        .toList();
  }

  @Transactional(readOnly = true)
  public DailySummaryResponse getDailySummary(UUID ownerId) {
    UUID storeId = storeRepository.getByOwnerId(ownerId).getId();
    DailySummary summary = DailySummary.from(
        waitingRepository.countByStatusForStoreAndBusinessDate(storeId, currentBusinessDate(storeId))
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

  @Transactional
  public void postpone(UUID ownerId, UUID waitingId) {
    WaitingEntry postponed = loadOwnedEntry(ownerId, waitingId).entry().postpone();
    waitingRepository.save(postponed);
    eventPublisher.publishEvent(
        new WaitingPostponedEvent(postponed.getStoreId(), postponed.getId()));
  }

  @Transactional(readOnly = true)
  public List<TodayWaitingResponse> getTodayWaitings(UUID ownerId) {
    UUID storeId = storeRepository.getByOwnerId(ownerId).getId();
    return waitingRepository.findAllByStoreIdAndBusinessDate(storeId, currentBusinessDate(storeId))
        .stream()
        .map(TodayWaitingResponse::from)
        .toList();
  }

  public SseEmitter subscribeOwnerDashboard(UUID ownerId) {
    UUID storeId = storeRepository.getByOwnerId(ownerId).getId();
    return ssePublisher.subscribeOwner(storeId);
  }

  /** 매장의 현재 영업일. 설정이 없으면 기본값(05:00) 기준으로 계산한다. */
  private LocalDate currentBusinessDate(UUID storeId) {
    LocalDateTime now = LocalDateTime.now();
    return storeSettingsRepository.findByStoreId(storeId)
        .map(s -> s.businessDateOf(now))
        .orElseGet(() -> now.toLocalTime().isBefore(DEFAULT_BUSINESS_DAY_START)
            ? now.toLocalDate().minusDays(1)
            : now.toLocalDate());
  }

  private OwnerWaitingResponse toOwnerWaitingResponse(WaitingEntry entry, int graceMinutes) {
    long elapsedMinutes = ChronoUnit.MINUTES.between(entry.getCreatedAt(), LocalDateTime.now());
    LocalDateTime graceDeadline = entry.getCalledAt() == null
        ? null
        : entry.getCalledAt().plusMinutes(graceMinutes);
    return new OwnerWaitingResponse(
        entry.getId(), entry.getWaitingNumber(), PhoneNumberMasker.mask(entry.getPhoneNumber()),
        entry.getPartySize(), entry.getStatus(), elapsedMinutes, graceDeadline);
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
