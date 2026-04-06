package com.qrwait.api.domain.repository;

import com.qrwait.api.domain.model.Owner;
import java.util.Optional;
import java.util.UUID;

public interface OwnerRepository {

  Owner save(Owner owner);

  Optional<Owner> findByEmail(String email);

  Optional<Owner> findById(UUID id);
}
