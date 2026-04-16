package com.qrwait.api.waiting.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WaitingRepository {

  WaitingEntry save(WaitingEntry entry);

  Optional<WaitingEntry> findById(UUID id);

  List<WaitingEntry> findByStoreIdAndStatus(UUID storeId, WaitingStatus status);

  List<WaitingEntry> findActiveByStoreId(UUID storeId);

  int countByStoreIdAndStatus(UUID storeId, WaitingStatus status);

  long countByStoreIdAndStatusAndDate(UUID storeId, WaitingStatus status, LocalDate date);

  int findNextWaitingNumber(UUID storeId);

  List<WaitingEntry> findAllByStoreIdAndDate(UUID storeId, LocalDate date);
}
