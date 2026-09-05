package com.qrwait.api.waiting.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface WaitingRepository {

  WaitingEntry save(WaitingEntry entry);

  Optional<WaitingEntry> findById(UUID id);

  List<WaitingEntry> findByStoreIdAndStatus(UUID storeId, LocalDate businessDate, WaitingStatus status);

  List<WaitingEntry> findActiveByStoreId(UUID storeId, LocalDate businessDate);

  int countByStoreIdAndStatus(UUID storeId, LocalDate businessDate, WaitingStatus status);

  int findNextWaitingNumber(UUID storeId, LocalDate businessDate);

  List<WaitingEntry> findAllByStoreIdAndBusinessDate(UUID storeId, LocalDate businessDate);

  Map<WaitingStatus, Long> countByStatusForStoreAndBusinessDate(UUID storeId, LocalDate businessDate);

  /**
   * 가명처리 대상 조회. {@code limit}은 한 번의 배치 실행이 한 매장에서 가명처리할 최대
   * 건수다 — 배치는 멱등하고 매시간 다시 도니, 잔량은 다음 실행에서 마저 처리된다.
   */
  List<WaitingEntry> findPseudonymizationTargets(UUID storeId, LocalDate currentBusinessDate, int limit);
}
