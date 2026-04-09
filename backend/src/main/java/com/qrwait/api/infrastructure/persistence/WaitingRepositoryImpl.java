package com.qrwait.api.infrastructure.persistence;

import com.qrwait.api.domain.model.WaitingEntry;
import com.qrwait.api.domain.model.WaitingStatus;
import com.qrwait.api.domain.repository.WaitingRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

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
  public List<WaitingEntry> findActiveByStoreId(UUID storeId) {
    List<String> activeStatuses = List.of(WaitingStatus.WAITING.name(), WaitingStatus.CALLED.name());
    return waitingEntryJpaRepository.findByStoreIdAndStatusInOrderByCreatedAtAsc(storeId, activeStatuses)
        .stream()
                .map(WaitingEntryJpaEntity::toDomain)
                .toList();
    }

    @Override
    public int countByStoreIdAndStatus(UUID storeId, WaitingStatus status) {
        return waitingEntryJpaRepository.countByStoreIdAndStatus(storeId, status.name());
    }

  @Override
  public long countByStoreIdAndStatusAndDate(UUID storeId, WaitingStatus status, LocalDate date) {
    LocalDateTime startOfDay = date.atStartOfDay();
    LocalDateTime endOfDay = date.plusDays(1).atStartOfDay();
    return waitingEntryJpaRepository.countByStoreIdAndStatusAndDate(storeId, status.name(), startOfDay, endOfDay);
  }

    @Override
    public int findNextWaitingNumber(UUID storeId) {
        return waitingEntryJpaRepository.findMaxWaitingNumberByStoreId(storeId) + 1;
    }
}
