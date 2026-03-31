package com.qrwait.api.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface WaitingEntryJpaRepository extends JpaRepository<WaitingEntryJpaEntity, UUID> {

    List<WaitingEntryJpaEntity> findByStoreIdAndStatus(UUID storeId, String status);

    int countByStoreIdAndStatus(UUID storeId, String status);

    @Query("SELECT COALESCE(MAX(w.waitingNumber), 0) FROM WaitingEntryJpaEntity w WHERE w.storeId = :storeId")
    int findMaxWaitingNumberByStoreId(@Param("storeId") UUID storeId);
}
