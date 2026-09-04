package com.qrwait.api.waiting.infrastructure;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WaitingEntryJpaRepository extends JpaRepository<WaitingEntryJpaEntity, UUID> {

  List<WaitingEntryJpaEntity> findByStoreIdAndBusinessDateAndStatus(
      UUID storeId, LocalDate businessDate, String status);

  int countByStoreIdAndBusinessDateAndStatus(UUID storeId, LocalDate businessDate, String status);

  List<WaitingEntryJpaEntity> findByStoreIdAndBusinessDateAndStatusInOrderByCreatedAtAsc(
      UUID storeId, LocalDate businessDate, List<String> statuses);

  @Query("""
      SELECT COALESCE(MAX(w.waitingNumber), 0)
        FROM WaitingEntryJpaEntity w
       WHERE w.storeId = :storeId
         AND w.businessDate = :businessDate
      """)
  int findMaxWaitingNumberByStoreIdAndBusinessDate(@Param("storeId") UUID storeId,
      @Param("businessDate") LocalDate businessDate);

  @Query("""
        SELECT w
          FROM WaitingEntryJpaEntity w
         WHERE w.storeId = :storeId
           AND w.businessDate = :businessDate
      ORDER BY w.waitingNumber DESC
      """)
  List<WaitingEntryJpaEntity> findAllByStoreIdAndBusinessDate(@Param("storeId") UUID storeId,
      @Param("businessDate") LocalDate businessDate);

  @Query("""
        SELECT w.status AS status, COUNT(w) AS count
          FROM WaitingEntryJpaEntity w
         WHERE w.storeId = :storeId
           AND w.businessDate = :businessDate
      GROUP BY w.status
      """)
  List<StatusCount> countByStatusGrouped(@Param("storeId") UUID storeId,
      @Param("businessDate") LocalDate businessDate);

  interface StatusCount {

    String getStatus();

    long getCount();
  }

  @Query("""
        SELECT w
          FROM WaitingEntryJpaEntity w
         WHERE w.storeId = :storeId
           AND w.businessDate < :currentBusinessDate
           AND w.phoneHash IS NULL
      ORDER BY w.businessDate, w.id
      """)
  List<WaitingEntryJpaEntity> findPseudonymizationTargets(@Param("storeId") UUID storeId,
      @Param("currentBusinessDate") LocalDate currentBusinessDate, Limit limit);
}
