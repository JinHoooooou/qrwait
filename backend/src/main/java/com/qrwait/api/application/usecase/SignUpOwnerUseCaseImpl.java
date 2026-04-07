package com.qrwait.api.application.usecase;

import com.qrwait.api.application.dto.SignUpRequest;
import com.qrwait.api.application.dto.SignUpResponse;
import com.qrwait.api.domain.model.DuplicateEmailException;
import com.qrwait.api.domain.model.Owner;
import com.qrwait.api.domain.model.Store;
import com.qrwait.api.domain.model.StoreSettings;
import com.qrwait.api.domain.repository.OwnerRepository;
import com.qrwait.api.domain.repository.StoreRepository;
import com.qrwait.api.domain.repository.StoreSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SignUpOwnerUseCaseImpl implements SignUpOwnerUseCase {

  private final OwnerRepository ownerRepository;
  private final StoreRepository storeRepository;
  private final StoreSettingsRepository storeSettingsRepository;
  private final PasswordEncoder passwordEncoder;

  @Value("${app.base-url}")
  private String baseUrl;

  @Override
  @Transactional
  public SignUpResponse execute(SignUpRequest request) {
    if (ownerRepository.findByEmail(request.getEmail()).isPresent()) {
      throw new DuplicateEmailException(request.getEmail());
    }

    String passwordHash = passwordEncoder.encode(request.getPassword());
    Owner owner = ownerRepository.save(Owner.create(request.getEmail(), passwordHash));

    Store store = storeRepository.save(Store.create(owner.getId(), request.getStoreName(), request.getAddress()));

    storeSettingsRepository.save(StoreSettings.createDefault(store.getId()));

    String qrUrl = baseUrl + "/wait?storeId=" + store.getId();

    return new SignUpResponse(owner.getId(), store.getId(), qrUrl);
  }
}
