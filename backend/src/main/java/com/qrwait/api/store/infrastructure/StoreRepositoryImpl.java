package com.qrwait.api.store.infrastructure;

import com.qrwait.api.store.domain.Store;
import com.qrwait.api.store.domain.StoreRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class StoreRepositoryImpl implements StoreRepository {

  private final StoreJpaRepository storeJpaRepository;

  @Override
  public Optional<Store> findById(UUID id) {
    return storeJpaRepository.findById(id).map(StoreJpaEntity::toDomain);
  }

  @Override
  public Optional<Store> findByOwnerId(UUID ownerId) {
    return storeJpaRepository.findByOwnerId(ownerId).map(StoreJpaEntity::toDomain);
  }

  @Override
  public Store save(Store store) {
    return storeJpaRepository.save(StoreJpaEntity.from(store)).toDomain();
  }
}
