package com.qrwait.api.infrastructure.persistence;

import com.qrwait.api.domain.model.Store;
import com.qrwait.api.domain.repository.StoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class StoreRepositoryImpl implements StoreRepository {

    private final StoreJpaRepository storeJpaRepository;

    @Override
    public Optional<Store> findById(UUID id) {
        return storeJpaRepository.findById(id)
                .map(StoreJpaEntity::toDomain);
    }

    @Override
    public Optional<Store> findByQrCode(String qrCode) {
        return storeJpaRepository.findByQrCode(qrCode)
                .map(StoreJpaEntity::toDomain);
    }

    @Override
    public Store save(Store store) {
        return storeJpaRepository.save(StoreJpaEntity.from(store)).toDomain();
    }
}
