package com.qrwait.api.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface StoreJpaRepository extends JpaRepository<StoreJpaEntity, UUID> {

    Optional<StoreJpaEntity> findByQrCode(String qrCode);
}
