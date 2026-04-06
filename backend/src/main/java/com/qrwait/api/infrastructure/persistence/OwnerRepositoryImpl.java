package com.qrwait.api.infrastructure.persistence;

import com.qrwait.api.domain.model.Owner;
import com.qrwait.api.domain.repository.OwnerRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class OwnerRepositoryImpl implements OwnerRepository {

  private final OwnerJpaRepository ownerJpaRepository;

  @Override
  public Owner save(Owner owner) {
    return ownerJpaRepository.save(OwnerJpaEntity.from(owner)).toDomain();
  }

  @Override
  public Optional<Owner> findByEmail(String email) {
    return ownerJpaRepository.findByEmail(email).map(OwnerJpaEntity::toDomain);
  }

  @Override
  public Optional<Owner> findById(UUID id) {
    return ownerJpaRepository.findById(id).map(OwnerJpaEntity::toDomain);
  }
}
