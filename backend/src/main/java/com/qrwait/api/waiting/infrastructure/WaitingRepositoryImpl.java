package com.qrwait.api.waiting.infrastructure;

import com.qrwait.api.waiting.domain.WaitingEntry;
import com.qrwait.api.waiting.domain.WaitingRepository;
import com.qrwait.api.waiting.domain.WaitingStatus;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
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
  public List<WaitingEntry> findByStoreIdAndStatus(UUID storeId, LocalDate businessDate, WaitingStatus status) {
    return waitingEntryJpaRepository
        .findByStoreIdAndBusinessDateAndStatus(storeId, businessDate, status.name())
        .stream()
        .map(WaitingEntryJpaEntity::toDomain)
        .toList();
  }

  @Override
  public List<WaitingEntry> findActiveByStoreId(UUID storeId, LocalDate businessDate) {
    List<String> activeStatuses = List.of(WaitingStatus.WAITING.name(), WaitingStatus.CALLED.name());
    return waitingEntryJpaRepository
        .findByStoreIdAndBusinessDateAndStatusInOrderByCreatedAtAsc(storeId, businessDate, activeStatuses)
        .stream()
        .map(WaitingEntryJpaEntity::toDomain)
        .toList();
  }

  @Override
  public int countByStoreIdAndStatus(UUID storeId, LocalDate businessDate, WaitingStatus status) {
    return waitingEntryJpaRepository.countByStoreIdAndBusinessDateAndStatus(storeId, businessDate, status.name());
  }

  @Override
  public int findNextWaitingNumber(UUID storeId, LocalDate businessDate) {
    return waitingEntryJpaRepository
        .findMaxWaitingNumberByStoreIdAndBusinessDate(storeId, businessDate) + 1;
  }

  @Override
  public List<WaitingEntry> findAllByStoreIdAndBusinessDate(UUID storeId, LocalDate businessDate) {
    return waitingEntryJpaRepository.findAllByStoreIdAndBusinessDate(storeId, businessDate)
        .stream()
        .map(WaitingEntryJpaEntity::toDomain)
        .toList();
  }

  @Override
  public Map<WaitingStatus, Long> countByStatusForStoreAndBusinessDate(UUID storeId, LocalDate businessDate) {
    Map<WaitingStatus, Long> result = new EnumMap<>(WaitingStatus.class);
    for (WaitingEntryJpaRepository.StatusCount row :
        waitingEntryJpaRepository.countByStatusGrouped(storeId, businessDate)) {
      result.put(WaitingStatus.valueOf(row.getStatus()), row.getCount());
    }
    return result;
  }

  @Override
  public List<WaitingEntry> findPseudonymizationTargets(UUID storeId, LocalDate currentBusinessDate,
      int limit) {
    return waitingEntryJpaRepository
        .findPseudonymizationTargets(storeId, currentBusinessDate, Limit.of(limit))
        .stream()
        .map(WaitingEntryJpaEntity::toDomain)
        .toList();
  }
}
