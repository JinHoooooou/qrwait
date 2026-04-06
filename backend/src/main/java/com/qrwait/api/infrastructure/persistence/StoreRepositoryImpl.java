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
  public Optional<Store> findByOwnerId(UUID ownerId) {
    // TODO 1-5: DB 컬럼 추가 후 구현 (현재 owner_id 컬럼 없음)
    throw new UnsupportedOperationException("owner_id 컬럼 추가 후 구현 예정");
  }

  @Override
  public Store save(Store store) {
    return storeJpaRepository.save(StoreJpaEntity.from(store)).toDomain();
  }
}
