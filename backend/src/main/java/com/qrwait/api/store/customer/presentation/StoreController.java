package com.qrwait.api.store.customer.presentation;

import com.qrwait.api.shared.sse.SsePublisher;
import com.qrwait.api.store.application.dto.StoreResponse;
import com.qrwait.api.store.customer.application.StoreViewService;
import com.qrwait.api.waiting.customer.application.WaitingService;
import com.qrwait.api.waiting.customer.dto.WaitingStatusResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Tag(name = "Store", description = "매장 관련 API")
@RestController
@RequestMapping("/api/stores")
@RequiredArgsConstructor
public class StoreController {

  private final StoreViewService storeViewService;
  private final WaitingService waitingService;
  private final SsePublisher ssePublisher;

  @Operation(summary = "매장 조회", description = "storeId로 매장 정보를 조회합니다.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "조회 성공"),
      @ApiResponse(responseCode = "404", description = "매장을 찾을 수 없음")
  })
  @GetMapping("/{storeId}")
  public ResponseEntity<StoreResponse> getStoreById(@PathVariable UUID storeId) {
    return ResponseEntity.ok(storeViewService.getStoreById(storeId));
  }

  @Operation(summary = "QR 코드 이미지 조회", description = "매장의 QR 코드 PNG 이미지를 반환합니다.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "QR 이미지 반환 성공"),
      @ApiResponse(responseCode = "404", description = "매장을 찾을 수 없음")
  })
  @GetMapping("/{storeId}/qr")
  public ResponseEntity<byte[]> getStoreQrImage(@PathVariable UUID storeId) {
    return ResponseEntity.ok()
        .contentType(MediaType.IMAGE_PNG)
        .body(storeViewService.generateQrImage(storeId));
  }

  @Operation(summary = "매장 대기 현황 조회", description = "해당 매장의 현재 대기 중인 팀 수를 조회합니다.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "조회 성공"),
      @ApiResponse(responseCode = "404", description = "매장을 찾을 수 없음")
  })
  @GetMapping("/{storeId}/waitings/status")
  public ResponseEntity<WaitingStatusResponse> getStoreWaitingStatus(@PathVariable UUID storeId) {
    return ResponseEntity.ok(waitingService.getStoreWaitingStatus(storeId));
  }

  @Operation(summary = "매장 상태 실시간 구독 (SSE)",
      description = "등록 전 손님이 매장 영업 상태 변경(운영중/브레이크타임/만석/영업종료)을 실시간으로 수신합니다.")
  @ApiResponse(responseCode = "200", description = "SSE 스트림 연결 성공")
  @GetMapping(value = "/{storeId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter stream(@PathVariable UUID storeId) {
    return ssePublisher.subscribeToStore(storeId);
  }
}
