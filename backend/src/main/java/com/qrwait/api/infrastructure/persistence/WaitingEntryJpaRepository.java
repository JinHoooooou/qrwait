package com.qrwait.api.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WaitingEntryJpaRepository extends JpaRepository<WaitingEntryJpaEntity, UUID> {

    List<WaitingEntryJpaEntity> findByStoreIdAndStatus(UUID storeId, String status);

  List<WaitingEntryJpaEntity> findByStoreIdAndStatusInOrderByCreatedAtAsc(UUID storeId, List<String> statuses);

    int countByStoreIdAndStatus(UUID storeId, String status);

    @Query("SELECT COALESCE(MAX(w.waitingNumber), 0) FROM WaitingEntryJpaEntity w WHERE w.storeId = :storeId")
    int findMaxWaitingNumberByStoreId(@Param("storeId") UUID storeId);
}
