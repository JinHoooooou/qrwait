package com.qrwait.api.presentation.controller;

import com.qrwait.api.application.dto.StoreResponse;
import com.qrwait.api.application.dto.StoreSettingsResponse;
import com.qrwait.api.application.dto.UpdateStoreInfoRequest;
import com.qrwait.api.application.dto.UpdateStoreSettingsRequest;
import com.qrwait.api.application.dto.UpdateStoreStatusRequest;
import com.qrwait.api.application.usecase.GetMyStoreUseCase;
import com.qrwait.api.application.usecase.GetStoreSettingsUseCase;
import com.qrwait.api.application.usecase.UpdateStoreInfoUseCase;
import com.qrwait.api.application.usecase.UpdateStoreSettingsUseCase;
import com.qrwait.api.application.usecase.UpdateStoreStatusUseCase;
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
public class OwnerController {

  private final GetMyStoreUseCase getMyStoreUseCase;
  private final UpdateStoreInfoUseCase updateStoreInfoUseCase;
  private final GetStoreSettingsUseCase getStoreSettingsUseCase;
  private final UpdateStoreSettingsUseCase updateStoreSettingsUseCase;
  private final UpdateStoreStatusUseCase updateStoreStatusUseCase;

  @Operation(summary = "내 매장 정보 조회")
  @GetMapping("/stores/me")
  public ResponseEntity<StoreResponse> getMyStore(@AuthenticationPrincipal UUID ownerId) {
    return ResponseEntity.ok(getMyStoreUseCase.execute(ownerId));
  }

  @Operation(summary = "매장 정보 수정 (이름, 주소)")
  @PutMapping("/stores/me")
  public ResponseEntity<StoreResponse> updateStoreInfo(
      @AuthenticationPrincipal UUID ownerId,
      @Valid @RequestBody UpdateStoreInfoRequest request) {
    return ResponseEntity.ok(updateStoreInfoUseCase.execute(ownerId, request));
  }

  @Operation(summary = "매장 설정 조회")
  @GetMapping("/stores/me/settings")
  public ResponseEntity<StoreSettingsResponse> getStoreSettings(@AuthenticationPrincipal UUID ownerId) {
    UUID storeId = getMyStoreUseCase.execute(ownerId).storeId();
    return ResponseEntity.ok(getStoreSettingsUseCase.execute(storeId));
  }

  @Operation(summary = "매장 설정 수정")
  @PutMapping("/stores/me/settings")
  public ResponseEntity<StoreSettingsResponse> updateStoreSettings(
      @AuthenticationPrincipal UUID ownerId,
      @Valid @RequestBody UpdateStoreSettingsRequest request) {
    UUID storeId = getMyStoreUseCase.execute(ownerId).storeId();
    return ResponseEntity.ok(updateStoreSettingsUseCase.execute(ownerId, storeId, request));
  }

  @Operation(summary = "매장 상태 변경")
  @PutMapping("/stores/me/status")
  public ResponseEntity<StoreResponse> updateStoreStatus(
      @AuthenticationPrincipal UUID ownerId,
      @Valid @RequestBody UpdateStoreStatusRequest request) {
    UUID storeId = getMyStoreUseCase.execute(ownerId).storeId();
    return ResponseEntity.ok(updateStoreStatusUseCase.execute(ownerId, storeId, request));
  }
}
