package com.qrwait.api.store.application;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.qrwait.api.store.application.dto.StoreResponse;
import com.qrwait.api.store.application.dto.UpdateStoreInfoRequest;
import com.qrwait.api.store.application.dto.UpdateStoreStatusRequest;
import com.qrwait.api.store.domain.Store;
import com.qrwait.api.store.domain.StoreNotFoundException;
import com.qrwait.api.store.domain.StoreRepository;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StoreService {

  private final StoreRepository storeRepository;

  @Value("${app.base-url}")
  private String baseUrl;

  @Transactional(readOnly = true)
  public StoreResponse getMyStore(UUID ownerId) {
    Store store = storeRepository.findByOwnerId(ownerId)
        .orElseThrow(() -> new StoreNotFoundException("ownerId=" + ownerId));
    return toResponse(store);
  }

  @Transactional
  public StoreResponse updateStoreInfo(UUID ownerId, UpdateStoreInfoRequest request) {
    Store store = storeRepository.findByOwnerId(ownerId)
        .orElseThrow(() -> new StoreNotFoundException("ownerId=" + ownerId));
    Store updated = storeRepository.save(store.updateInfo(request.getName(), request.getAddress()));
    return toResponse(updated);
  }

  @Transactional
  public StoreResponse updateStoreStatus(UUID ownerId, UpdateStoreStatusRequest request) {
    Store store = storeRepository.findByOwnerId(ownerId)
        .orElseThrow(() -> new StoreNotFoundException("ownerId=" + ownerId));
    Store updated = storeRepository.save(store.changeStatus(request.getStatus()));
    return toResponse(updated);
  }

  @Transactional(readOnly = true)
  public StoreResponse getStoreById(UUID storeId) {
    Store store = storeRepository.findById(storeId)
        .orElseThrow(() -> new StoreNotFoundException(storeId));
    return toResponse(store);
  }

  public byte[] generateQrImage(UUID storeId) {
    storeRepository.findById(storeId)
        .orElseThrow(() -> new StoreNotFoundException(storeId));

    String qrUrl = baseUrl + "/wait?storeId=" + storeId;

    try {
      QRCodeWriter writer = new QRCodeWriter();
      BitMatrix bitMatrix = writer.encode(qrUrl, BarcodeFormat.QR_CODE, 300, 300);
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      MatrixToImageWriter.writeToStream(bitMatrix, "PNG", out);
      return out.toByteArray();
    } catch (WriterException | IOException e) {
      throw new RuntimeException("QR 코드 생성 실패", e);
    }
  }

  private StoreResponse toResponse(Store store) {
    return new StoreResponse(store.getId(), store.getName(), store.getAddress(), store.getStatus());
  }
}
