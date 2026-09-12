package com.qrwait.api.store.customer.presentation;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qrwait.api.shared.security.JwtAuthFilter;
import com.qrwait.api.shared.security.JwtTokenProvider;
import com.qrwait.api.shared.security.SecurityConfig;
import com.qrwait.api.shared.sse.SsePublisher;
import com.qrwait.api.store.application.dto.StoreResponse;
import com.qrwait.api.store.customer.application.StoreViewService;
import com.qrwait.api.store.domain.StoreNotFoundException;
import com.qrwait.api.store.domain.StoreStatus;
import com.qrwait.api.waiting.customer.application.WaitingService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@WebMvcTest(StoreController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class})
@ActiveProfiles("test")
class StoreControllerTest {

  @Autowired
  MockMvc mockMvc;
  @Autowired
  ObjectMapper objectMapper;
  @MockitoBean
  JwtTokenProvider jwtTokenProvider;
  @MockitoBean
  StoreViewService storeViewService;
  @MockitoBean
  WaitingService waitingService;
  @MockitoBean
  SsePublisher ssePublisher;

  @Test
  void getStore_존재하는_storeId_200반환() throws Exception {
    UUID storeId = UUID.randomUUID();
    given(storeViewService.getStoreById(storeId))
        .willReturn(new StoreResponse(storeId, "테스트 식당", "서울시 강남구", StoreStatus.OPEN));

    mockMvc.perform(get("/api/stores/" + storeId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.storeId").value(storeId.toString()))
        .andExpect(jsonPath("$.name").value("테스트 식당"));
  }

  @Test
  void getStore_존재하지않는_storeId_404반환() throws Exception {
    UUID unknownId = UUID.randomUUID();
    given(storeViewService.getStoreById(unknownId))
        .willThrow(new StoreNotFoundException(unknownId));

    mockMvc.perform(get("/api/stores/" + unknownId))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("STORE_NOT_FOUND"));
  }

  @Test
  void stream_비로그인_손님도_200반환() throws Exception {
    UUID storeId = UUID.randomUUID();
    given(ssePublisher.subscribeToStore(eq(storeId))).willReturn(new SseEmitter());

    mockMvc.perform(get("/api/stores/{storeId}/stream", storeId))
        .andExpect(status().isOk());
  }
}
