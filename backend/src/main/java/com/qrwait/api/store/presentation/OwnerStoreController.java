package com.qrwait.api.store.presentation;

import com.qrwait.api.store.application.StoreService;
import com.qrwait.api.store.application.StoreSettingsService;
import com.qrwait.api.store.application.dto.StoreResponse;
import com.qrwait.api.store.application.dto.StoreSettingsResponse;
import com.qrwait.api.store.application.dto.UpdateStoreInfoRequest;
import com.qrwait.api.store.application.dto.UpdateStoreSettingsRequest;
import com.qrwait.api.store.application.dto.UpdateStoreStatusRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Owner - Store", description = "점주 매장 관리 API")
@RestController
@RequestMapping("/api/owner")
@RequiredArgsConstructor
public class OwnerStoreController {

  private final StoreService storeService;
  private final StoreSettingsService storeSettingsService;

  @Operation(summary = "내 매장 정보 조회")
  @GetMapping("/stores/me")
  public ResponseEntity<StoreResponse> getMyStore(@AuthenticationPrincipal UUID ownerId) {
    return ResponseEntity.ok(storeService.getMyStore(ownerId));
  }

  @Operation(summary = "매장 정보 수정 (이름, 주소)")
  @PutMapping("/stores/me")
  public ResponseEntity<StoreResponse> updateStoreInfo(
      @AuthenticationPrincipal UUID ownerId,
      @Valid @RequestBody UpdateStoreInfoRequest request) {
    return ResponseEntity.ok(storeService.updateStoreInfo(ownerId, request));
  }

  @Operation(summary = "매장 상태 변경")
  @PutMapping("/stores/me/status")
  public ResponseEntity<StoreResponse> updateStoreStatus(
      @AuthenticationPrincipal UUID ownerId,
      @Valid @RequestBody UpdateStoreStatusRequest request) {
    return ResponseEntity.ok(storeService.updateStoreStatus(ownerId, request));
  }

  @Operation(summary = "매장 설정 조회")
  @GetMapping("/stores/me/settings")
  public ResponseEntity<StoreSettingsResponse> getStoreSettings(@AuthenticationPrincipal UUID ownerId) {
    return ResponseEntity.ok(storeSettingsService.getSettings(ownerId));
  }

  @Operation(summary = "매장 설정 수정")
  @PutMapping("/stores/me/settings")
  public ResponseEntity<StoreSettingsResponse> updateStoreSettings(
      @AuthenticationPrincipal UUID ownerId,
      @Valid @RequestBody UpdateStoreSettingsRequest request) {
    return ResponseEntity.ok(storeSettingsService.updateSettings(ownerId, request));
  }
}
