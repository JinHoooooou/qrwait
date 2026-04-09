package com.qrwait.api.store.presentation;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qrwait.api.shared.security.JwtAuthFilter;
import com.qrwait.api.shared.security.JwtTokenProvider;
import com.qrwait.api.shared.security.SecurityConfig;
import com.qrwait.api.store.application.StoreService;
import com.qrwait.api.store.application.dto.StoreResponse;
import com.qrwait.api.store.domain.StoreNotFoundException;
import com.qrwait.api.store.domain.StoreStatus;
import com.qrwait.api.waiting.application.WaitingService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(StoreController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class})
class StoreControllerTest {

  @Autowired
  MockMvc mockMvc;
  @Autowired
  ObjectMapper objectMapper;
  @MockitoBean
  JwtTokenProvider jwtTokenProvider;
  @MockitoBean
  StoreService storeService;
  @MockitoBean
  WaitingService waitingService;

  @Test
  void getStore_존재하는_storeId_200반환() throws Exception {
    UUID storeId = UUID.randomUUID();
    given(storeService.getStoreById(storeId))
        .willReturn(new StoreResponse(storeId, "테스트 식당", "서울시 강남구", StoreStatus.OPEN));

    mockMvc.perform(get("/api/stores/" + storeId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.storeId").value(storeId.toString()))
        .andExpect(jsonPath("$.name").value("테스트 식당"));
  }

  @Test
  void getStore_존재하지않는_storeId_404반환() throws Exception {
    UUID unknownId = UUID.randomUUID();
    given(storeService.getStoreById(unknownId))
        .willThrow(new StoreNotFoundException(unknownId));

    mockMvc.perform(get("/api/stores/" + unknownId))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("STORE_NOT_FOUND"));
  }
}
