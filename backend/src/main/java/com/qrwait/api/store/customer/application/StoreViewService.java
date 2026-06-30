package com.qrwait.api.store.customer.application;

import com.qrwait.api.shared.qr.QrCodeGenerator;
import com.qrwait.api.store.application.dto.StoreResponse;
import com.qrwait.api.store.domain.Store;
import com.qrwait.api.store.domain.StoreRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StoreViewService {

  private final StoreRepository storeRepository;
  private final QrCodeGenerator qrCodeGenerator;

  @Value("${app.base-url}")
  private String baseUrl;

  @Transactional(readOnly = true)
  public StoreResponse getStoreById(UUID storeId) {
    Store store = storeRepository.getById(storeId);
    return new StoreResponse(store.getId(), store.getName(), store.getAddress(), store.getStatus());
  }

  public byte[] generateQrImage(UUID storeId) {
    storeRepository.getById(storeId);
    String qrUrl = baseUrl + "/wait?storeId=" + storeId;
    return qrCodeGenerator.generate(qrUrl);
  }
}
