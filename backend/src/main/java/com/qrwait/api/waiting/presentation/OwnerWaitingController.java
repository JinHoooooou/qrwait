package com.qrwait.api.waiting.presentation;

import com.qrwait.api.waiting.application.WaitingManagementService;
import com.qrwait.api.waiting.application.dto.DailySummaryResponse;
import com.qrwait.api.waiting.application.dto.OwnerWaitingResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Owner - Waiting", description = "점주 웨이팅 관리 API")
@RestController
@RequestMapping("/api/owner")
@RequiredArgsConstructor
public class OwnerWaitingController {

  private final WaitingManagementService waitingManagementService;

  @Operation(summary = "현재 대기 목록 조회")
  @GetMapping("/stores/me/waitings")
  public ResponseEntity<List<OwnerWaitingResponse>> getWaitingList(@AuthenticationPrincipal UUID ownerId) {
    return ResponseEntity.ok(waitingManagementService.getWaitingList(ownerId));
  }

  @Operation(summary = "오늘 대기 통계 조회")
  @GetMapping("/stores/me/waitings/summary")
  public ResponseEntity<DailySummaryResponse> getDailySummary(@AuthenticationPrincipal UUID ownerId) {
    return ResponseEntity.ok(waitingManagementService.getDailySummary(ownerId));
  }

  @Operation(summary = "손님 호출")
  @PostMapping("/waitings/{waitingId}/call")
  public ResponseEntity<Void> callWaiting(
      @AuthenticationPrincipal UUID ownerId,
      @PathVariable UUID waitingId) {
    waitingManagementService.call(ownerId, waitingId);
    return ResponseEntity.noContent().build();
  }

  @Operation(summary = "입장 처리")
  @PostMapping("/waitings/{waitingId}/enter")
  public ResponseEntity<Void> enterWaiting(
      @AuthenticationPrincipal UUID ownerId,
      @PathVariable UUID waitingId) {
    waitingManagementService.enter(ownerId, waitingId);
    return ResponseEntity.noContent().build();
  }

  @Operation(summary = "노쇼 처리")
  @PostMapping("/waitings/{waitingId}/noshow")
  public ResponseEntity<Void> noShowWaiting(
      @AuthenticationPrincipal UUID ownerId,
      @PathVariable UUID waitingId) {
    waitingManagementService.noShow(ownerId, waitingId);
    return ResponseEntity.noContent().build();
  }
}
