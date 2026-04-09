package com.qrwait.api.owner.domain;

import java.util.Optional;
import java.util.UUID;

public interface OwnerRepository {

  Owner save(Owner owner);

  Optional<Owner> findByEmail(String email);

  Optional<Owner> findById(UUID id);
}
