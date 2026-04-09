package com.qrwait.api.waiting.application;

import com.qrwait.api.store.application.StoreService;
import com.qrwait.api.store.domain.StoreNotFoundException;
import com.qrwait.api.waiting.application.dto.DailySummaryResponse;
import com.qrwait.api.waiting.application.dto.OwnerWaitingResponse;
import com.qrwait.api.waiting.domain.WaitingEntry;
import com.qrwait.api.waiting.domain.WaitingNotFoundException;
import com.qrwait.api.waiting.domain.WaitingRepository;
import com.qrwait.api.waiting.domain.WaitingStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WaitingManagementService {

  private final WaitingRepository waitingRepository;
  private final StoreService storeService;

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

    entry.call();
    waitingRepository.save(entry);

    // TODO: Phase 4에서 SSE 추가 — 해당 손님 채널에 'called' 이벤트 발송
  }

  @Transactional
  public void enter(UUID ownerId, UUID waitingId) {
    WaitingEntry entry = waitingRepository.findById(waitingId)
        .orElseThrow(() -> new WaitingNotFoundException(waitingId));

    UUID ownerStoreId = resolveStoreId(ownerId);
    if (!ownerStoreId.equals(entry.getStoreId())) {
      throw new StoreNotFoundException("ownerId=" + ownerId);
    }

    entry.enter();
    waitingRepository.save(entry);

    // TODO: Phase 4에서 SSE 추가 — 매장 전체 브로드캐스트
  }

  @Transactional
  public void noShow(UUID ownerId, UUID waitingId) {
    WaitingEntry entry = waitingRepository.findById(waitingId)
        .orElseThrow(() -> new WaitingNotFoundException(waitingId));

    UUID ownerStoreId = resolveStoreId(ownerId);
    if (!ownerStoreId.equals(entry.getStoreId())) {
      throw new StoreNotFoundException("ownerId=" + ownerId);
    }

    entry.noShow();
    waitingRepository.save(entry);

    // TODO: Phase 4에서 SSE 추가 — 매장 전체 브로드캐스트
  }

  private UUID resolveStoreId(UUID ownerId) {
    return storeService.getMyStore(ownerId).storeId();
  }

  private OwnerWaitingResponse toOwnerWaitingResponse(WaitingEntry entry) {
    long elapsedMinutes = ChronoUnit.MINUTES.between(entry.getCreatedAt(), LocalDateTime.now());
    return new OwnerWaitingResponse(
        entry.getId(),
        entry.getWaitingNumber(),
        entry.getVisitorName(),
        entry.getPartySize(),
        entry.getStatus(),
        elapsedMinutes
    );
  }
}
