package com.qrwait.api.application.usecase;

import com.qrwait.api.application.dto.CreateStoreRequest;
import com.qrwait.api.application.dto.CreateStoreResponse;
import com.qrwait.api.domain.model.Store;
import com.qrwait.api.domain.repository.StoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CreateStoreUseCaseImpl implements CreateStoreUseCase {

  private final StoreRepository storeRepository;

  @Value("${app.base-url}")
  private String baseUrl;

  @Override
  @Transactional
  public CreateStoreResponse execute(CreateStoreRequest request) {
    // TODO Phase 3: JWT에서 추출한 ownerId로 교체
    Store store = Store.create(null, request.getName(), null);
    Store saved = storeRepository.save(store);

    String qrUrl = baseUrl + "/wait?storeId=" + saved.getId();

    return new CreateStoreResponse(saved.getId(), saved.getName(), qrUrl);
  }
}
