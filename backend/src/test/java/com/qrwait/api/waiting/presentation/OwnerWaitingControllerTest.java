package com.qrwait.api.waiting.presentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qrwait.api.shared.security.JwtAuthFilter;
import com.qrwait.api.shared.security.JwtTokenProvider;
import com.qrwait.api.shared.security.SecurityConfig;
import com.qrwait.api.waiting.application.WaitingManagementService;
import com.qrwait.api.waiting.application.dto.DailySummaryResponse;
import com.qrwait.api.waiting.application.dto.OwnerWaitingResponse;
import com.qrwait.api.waiting.domain.WaitingStatus;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@WebMvcTest(OwnerWaitingController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class})
class OwnerWaitingControllerTest {

  @Autowired
  MockMvc mockMvc;
  @Autowired
  ObjectMapper objectMapper;

  @MockitoBean
  JwtTokenProvider jwtTokenProvider;
  @MockitoBean
  WaitingManagementService waitingManagementService;

  // ===== streamDashboard =====

  @Test
  void streamDashboard_인증없음_403반환() throws Exception {
    mockMvc.perform(get("/api/owner/stores/me/dashboard/stream"))
        .andExpect(status().isForbidden());
  }

  @Test
  void streamDashboard_인증된_점주_200반환() throws Exception {
    UUID ownerId = UUID.randomUUID();

    given(jwtTokenProvider.validateToken(any())).willReturn(true);
    given(jwtTokenProvider.extractOwnerId(any())).willReturn(ownerId);
    given(waitingManagementService.subscribeOwnerDashboard(eq(ownerId)))
        .willReturn(new SseEmitter());

    mockMvc.perform(get("/api/owner/stores/me/dashboard/stream")
            .header("Authorization", "Bearer test-token"))
        .andExpect(status().isOk());
  }

  // ===== getWaitingList =====

  @Test
  void getWaitingList_인증없음_403반환() throws Exception {
    mockMvc.perform(get("/api/owner/stores/me/waitings"))
        .andExpect(status().isForbidden());
  }

  @Test
  void getWaitingList_인증된_점주_200반환() throws Exception {
    UUID ownerId = UUID.randomUUID();
    UUID waitingId = UUID.randomUUID();

    given(jwtTokenProvider.validateToken(any())).willReturn(true);
    given(jwtTokenProvider.extractOwnerId(any())).willReturn(ownerId);
    given(waitingManagementService.getWaitingList(eq(ownerId)))
        .willReturn(List.of(
            new OwnerWaitingResponse(waitingId, 1, "홍길동", 2, WaitingStatus.WAITING, 5L)
        ));

    mockMvc.perform(get("/api/owner/stores/me/waitings")
            .header("Authorization", "Bearer test-token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].waitingId").value(waitingId.toString()))
        .andExpect(jsonPath("$[0].visitorName").value("홍길동"))
        .andExpect(jsonPath("$[0].waitingNumber").value(1));
  }

  // ===== getDailySummary =====

  @Test
  void getDailySummary_인증없음_403반환() throws Exception {
    mockMvc.perform(get("/api/owner/stores/me/waitings/summary"))
        .andExpect(status().isForbidden());
  }

  @Test
  void getDailySummary_인증된_점주_200반환() throws Exception {
    UUID ownerId = UUID.randomUUID();

    given(jwtTokenProvider.validateToken(any())).willReturn(true);
    given(jwtTokenProvider.extractOwnerId(any())).willReturn(ownerId);
    given(waitingManagementService.getDailySummary(eq(ownerId)))
        .willReturn(new DailySummaryResponse(10L, 7L, 1L, 2L, 3L));

    mockMvc.perform(get("/api/owner/stores/me/waitings/summary")
            .header("Authorization", "Bearer test-token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalRegistered").value(10))
        .andExpect(jsonPath("$.totalEntered").value(7))
        .andExpect(jsonPath("$.currentWaiting").value(3));
  }

  // ===== call =====

  @Test
  void callWaiting_인증없음_403반환() throws Exception {
    mockMvc.perform(post("/api/owner/waitings/" + UUID.randomUUID() + "/call"))
        .andExpect(status().isForbidden());
  }

  @Test
  void callWaiting_인증된_점주_204반환() throws Exception {
    UUID ownerId = UUID.randomUUID();
    UUID waitingId = UUID.randomUUID();

    given(jwtTokenProvider.validateToken(any())).willReturn(true);
    given(jwtTokenProvider.extractOwnerId(any())).willReturn(ownerId);
    willDoNothing().given(waitingManagementService).call(eq(ownerId), eq(waitingId));

    mockMvc.perform(post("/api/owner/waitings/" + waitingId + "/call")
            .header("Authorization", "Bearer test-token"))
        .andExpect(status().isNoContent());
  }

  // ===== enter =====

  @Test
  void enterWaiting_인증없음_403반환() throws Exception {
    mockMvc.perform(post("/api/owner/waitings/" + UUID.randomUUID() + "/enter"))
        .andExpect(status().isForbidden());
  }

  @Test
  void enterWaiting_인증된_점주_204반환() throws Exception {
    UUID ownerId = UUID.randomUUID();
    UUID waitingId = UUID.randomUUID();

    given(jwtTokenProvider.validateToken(any())).willReturn(true);
    given(jwtTokenProvider.extractOwnerId(any())).willReturn(ownerId);
    willDoNothing().given(waitingManagementService).enter(eq(ownerId), eq(waitingId));

    mockMvc.perform(post("/api/owner/waitings/" + waitingId + "/enter")
            .header("Authorization", "Bearer test-token"))
        .andExpect(status().isNoContent());
  }

  // ===== noShow =====

  @Test
  void noShowWaiting_인증없음_403반환() throws Exception {
    mockMvc.perform(post("/api/owner/waitings/" + UUID.randomUUID() + "/noshow"))
        .andExpect(status().isForbidden());
  }

  @Test
  void noShowWaiting_인증된_점주_204반환() throws Exception {
    UUID ownerId = UUID.randomUUID();
    UUID waitingId = UUID.randomUUID();

    given(jwtTokenProvider.validateToken(any())).willReturn(true);
    given(jwtTokenProvider.extractOwnerId(any())).willReturn(ownerId);
    willDoNothing().given(waitingManagementService).noShow(eq(ownerId), eq(waitingId));

    mockMvc.perform(post("/api/owner/waitings/" + waitingId + "/noshow")
            .header("Authorization", "Bearer test-token"))
        .andExpect(status().isNoContent());
  }
}
