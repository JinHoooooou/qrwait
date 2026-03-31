package com.qrwait.api.infrastructure.persistence;

import com.qrwait.api.domain.model.WaitingEntry;
import com.qrwait.api.domain.model.WaitingStatus;
import com.qrwait.api.domain.repository.WaitingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class WaitingRepositoryImpl implements WaitingRepository {

    private final WaitingEntryJpaRepository waitingEntryJpaRepository;

    @Override
    public WaitingEntry save(WaitingEntry entry) {
        return waitingEntryJpaRepository.save(WaitingEntryJpaEntity.from(entry)).toDomain();
    }

    @Override
    public Optional<WaitingEntry> findById(UUID id) {
        return waitingEntryJpaRepository.findById(id)
                .map(WaitingEntryJpaEntity::toDomain);
    }

    @Override
    public List<WaitingEntry> findByStoreIdAndStatus(UUID storeId, WaitingStatus status) {
        return waitingEntryJpaRepository.findByStoreIdAndStatus(storeId, status.name())
                .stream()
                .map(WaitingEntryJpaEntity::toDomain)
                .toList();
    }

    @Override
    public int countByStoreIdAndStatus(UUID storeId, WaitingStatus status) {
        return waitingEntryJpaRepository.countByStoreIdAndStatus(storeId, status.name());
    }

    @Override
    public int findNextWaitingNumber(UUID storeId) {
        return waitingEntryJpaRepository.findMaxWaitingNumberByStoreId(storeId) + 1;
    }
}
