package com.qrwait.api.waiting.presentation;

import com.qrwait.api.shared.sse.WaitingSseService;
import com.qrwait.api.waiting.application.WaitingService;
import com.qrwait.api.waiting.application.dto.RegisterWaitingRequest;
import com.qrwait.api.waiting.application.dto.RegisterWaitingResponse;
import com.qrwait.api.waiting.application.dto.WaitingStatusResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Tag(name = "Waiting", description = "웨이팅 관련 API")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class WaitingController {

  private final WaitingService waitingService;
  private final WaitingSseService waitingSseService;  // broadcast는 트랜잭션 커밋 후 호출

  @Operation(summary = "웨이팅 등록", description = "매장에 웨이팅을 등록합니다. 등록 성공 시 waitingId와 대기 순번을 반환합니다.")
  @ApiResponses({
      @ApiResponse(responseCode = "201", description = "웨이팅 등록 성공"),
      @ApiResponse(responseCode = "400", description = "유효하지 않은 요청"),
      @ApiResponse(responseCode = "404", description = "매장을 찾을 수 없음")
  })
  @PostMapping("/stores/{storeId}/waitings")
  public ResponseEntity<RegisterWaitingResponse> register(
      @PathVariable UUID storeId,
      @Valid @RequestBody RegisterWaitingRequest request) {
    RegisterWaitingResponse response = waitingService.register(storeId, request);
    waitingSseService.broadcastRegistered(storeId);  // 트랜잭션 커밋 후 broadcast
    URI location = URI.create("/api/waitings/" + response.waitingId());
    return ResponseEntity.created(location).body(response);
  }

  @Operation(summary = "웨이팅 상태 조회", description = "waitingId로 현재 대기 순번과 상태를 조회합니다.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "조회 성공"),
      @ApiResponse(responseCode = "404", description = "웨이팅을 찾을 수 없음")
  })
  @GetMapping("/waitings/{waitingId}")
  public ResponseEntity<WaitingStatusResponse> getStatus(@PathVariable UUID waitingId) {
    return ResponseEntity.ok(waitingService.getStatus(waitingId));
  }

  @Operation(summary = "웨이팅 취소", description = "진행 중인 웨이팅을 취소합니다.")
  @ApiResponses({
      @ApiResponse(responseCode = "204", description = "취소 성공"),
      @ApiResponse(responseCode = "404", description = "웨이팅을 찾을 수 없음"),
      @ApiResponse(responseCode = "409", description = "이미 취소되었거나 입장 완료된 웨이팅")
  })
  @DeleteMapping("/waitings/{waitingId}")
  public ResponseEntity<Void> cancel(@PathVariable UUID waitingId) {
    UUID storeId = waitingService.cancel(waitingId);
    waitingSseService.broadcastUpdate(storeId);  // 트랜잭션 커밋 후 broadcast
    return ResponseEntity.noContent().build();
  }

  @Operation(summary = "웨이팅 실시간 구독 (SSE)", description = "Server-Sent Events로 대기 상태 변경을 실시간으로 수신합니다.")
  @ApiResponse(responseCode = "200", description = "SSE 스트림 연결 성공")
  @GetMapping(value = "/waitings/{waitingId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter stream(
      @PathVariable UUID waitingId,
      @RequestParam UUID storeId) {
    return waitingSseService.subscribe(storeId, waitingId);
  }
}
