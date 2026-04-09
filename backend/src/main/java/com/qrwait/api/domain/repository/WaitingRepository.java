package com.qrwait.api.domain.repository;

import com.qrwait.api.domain.model.WaitingEntry;
import com.qrwait.api.domain.model.WaitingStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WaitingRepository {

    WaitingEntry save(WaitingEntry entry);

    Optional<WaitingEntry> findById(UUID id);

    List<WaitingEntry> findByStoreIdAndStatus(UUID storeId, WaitingStatus status);

  List<WaitingEntry> findActiveByStoreId(UUID storeId);

    int countByStoreIdAndStatus(UUID storeId, WaitingStatus status);

    int findNextWaitingNumber(UUID storeId);
}
