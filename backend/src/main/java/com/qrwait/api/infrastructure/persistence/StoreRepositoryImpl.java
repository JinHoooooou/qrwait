package com.qrwait.api.infrastructure.persistence;

import com.qrwait.api.domain.model.Store;
import com.qrwait.api.domain.repository.StoreRepository;
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
    return storeJpaRepository.findById(id)
        .map(StoreJpaEntity::toDomain);
  }

  @Override
  public Store save(Store store) {
    return storeJpaRepository.save(StoreJpaEntity.from(store)).toDomain();
  }
}
