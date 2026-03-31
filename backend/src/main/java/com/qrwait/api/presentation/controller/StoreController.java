package com.qrwait.api.presentation.controller;

import com.qrwait.api.application.dto.StoreResponse;
import com.qrwait.api.application.dto.WaitingStatusResponse;
import com.qrwait.api.application.usecase.GetStoreByQrCodeUseCase;
import com.qrwait.api.application.usecase.GetStoreWaitingStatusUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/stores")
@RequiredArgsConstructor
public class StoreController {

    private final GetStoreByQrCodeUseCase getStoreByQrCodeUseCase;
    private final GetStoreWaitingStatusUseCase getStoreWaitingStatusUseCase;

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
