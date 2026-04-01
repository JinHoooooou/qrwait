package com.qrwait.api.presentation.controller;

import com.qrwait.api.application.dto.RegisterWaitingRequest;
import com.qrwait.api.application.dto.RegisterWaitingResponse;
import com.qrwait.api.application.dto.WaitingStatusResponse;
import com.qrwait.api.application.usecase.CancelWaitingUseCase;
import com.qrwait.api.application.usecase.EnterWaitingUseCaseImpl;
import com.qrwait.api.application.usecase.GetWaitingStatusUseCase;
import com.qrwait.api.application.usecase.RegisterWaitingUseCase;
import com.qrwait.api.infrastructure.sse.WaitingSseService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class WaitingController {

    private final RegisterWaitingUseCase registerWaitingUseCase;
    private final GetWaitingStatusUseCase getWaitingStatusUseCase;
    private final CancelWaitingUseCase cancelWaitingUseCase;
    private final EnterWaitingUseCaseImpl enterWaitingUseCase;
    private final WaitingSseService waitingSseService;

    /** 웨이팅 등록 */
    @PostMapping("/stores/{storeId}/waitings")
    public ResponseEntity<RegisterWaitingResponse> register(
            @PathVariable UUID storeId,
            @Valid @RequestBody RegisterWaitingRequest request) {
        RegisterWaitingResponse response = registerWaitingUseCase.execute(storeId, request);
        URI location = URI.create("/api/waitings/" + response.waitingId());
        return ResponseEntity.created(location).body(response);
    }

    /** 웨이팅 상세 조회 */
    @GetMapping("/waitings/{waitingId}")
    public ResponseEntity<WaitingStatusResponse> getStatus(@PathVariable UUID waitingId) {
        return ResponseEntity.ok(getWaitingStatusUseCase.execute(waitingId));
    }

    /** 웨이팅 취소 */
    @DeleteMapping("/waitings/{waitingId}")
    public ResponseEntity<Void> cancel(@PathVariable UUID waitingId) {
        cancelWaitingUseCase.execute(waitingId);
        return ResponseEntity.noContent().build();
    }

    /**
     * SSE 구독 — 대기 상태 실시간 수신
     */
    @GetMapping(value = "/waitings/{waitingId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(
        @PathVariable UUID waitingId,
        @RequestParam UUID storeId) {
        return waitingSseService.subscribe(storeId, waitingId);
    }

    /**
     * 시뮬레이션용 입장 처리 — 점주 대시보드 구현 전 SSE 동작 검증용
     */
    @PutMapping("/waitings/{waitingId}/enter")
    public ResponseEntity<Void> enter(@PathVariable UUID waitingId) {
        enterWaitingUseCase.execute(waitingId);
        return ResponseEntity.noContent().build();
    }
}
