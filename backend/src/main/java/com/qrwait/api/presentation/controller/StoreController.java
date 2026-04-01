package com.qrwait.api.presentation.controller;

import com.qrwait.api.application.dto.CreateStoreRequest;
import com.qrwait.api.application.dto.CreateStoreResponse;
import com.qrwait.api.application.dto.StoreResponse;
import com.qrwait.api.application.dto.WaitingStatusResponse;
import com.qrwait.api.application.usecase.CreateStoreUseCase;
import com.qrwait.api.application.usecase.GetStoreByQrCodeUseCase;
import com.qrwait.api.application.usecase.GetStoreWaitingStatusUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/stores")
@RequiredArgsConstructor
public class StoreController {

    private final CreateStoreUseCase createStoreUseCase;
    private final GetStoreByQrCodeUseCase getStoreByQrCodeUseCase;
    private final GetStoreWaitingStatusUseCase getStoreWaitingStatusUseCase;

    /** 매장 등록 */
    @PostMapping
    public ResponseEntity<CreateStoreResponse> createStore(@Valid @RequestBody CreateStoreRequest request) {
        CreateStoreResponse response = createStoreUseCase.execute(request);
        URI location = URI.create("/api/stores/" + response.storeId());
        return ResponseEntity.created(location).body(response);
    }

    /** QR 코드로 매장 조회 */
    @GetMapping("/{qrCode}")
    public ResponseEntity<StoreResponse> getStoreByQrCode(@PathVariable String qrCode) {
        return ResponseEntity.ok(getStoreByQrCodeUseCase.execute(qrCode));
    }

    /** 매장 전체 대기 현황 조회 */
    @GetMapping("/{storeId}/waitings/status")
    public ResponseEntity<WaitingStatusResponse> getStoreWaitingStatus(@PathVariable UUID storeId) {
        return ResponseEntity.ok(getStoreWaitingStatusUseCase.execute(storeId));
    }
}
