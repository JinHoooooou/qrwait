package com.qrwait.api.store.presentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qrwait.api.shared.security.JwtAuthFilter;
import com.qrwait.api.shared.security.JwtTokenProvider;
import com.qrwait.api.shared.security.SecurityConfig;
import com.qrwait.api.store.application.StoreService;
import com.qrwait.api.store.application.StoreSettingsService;
import com.qrwait.api.store.application.dto.StoreResponse;
import com.qrwait.api.store.application.dto.StoreSettingsResponse;
import com.qrwait.api.store.domain.StoreStatus;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(OwnerStoreController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class})
class OwnerStoreControllerTest {

  @Autowired
  MockMvc mockMvc;
  @Autowired
  ObjectMapper objectMapper;
  @MockitoBean
  JwtTokenProvider jwtTokenProvider;
  @MockitoBean
  StoreService storeService;
  @MockitoBean
  StoreSettingsService storeSettingsService;

  @Test
  void getMyStore_인증된_점주_200반환() throws Exception {
    UUID ownerId = UUID.randomUUID();
    UUID storeId = UUID.randomUUID();

    given(storeService.getMyStore(eq(ownerId)))
        .willReturn(new StoreResponse(storeId, "테스트 식당", "서울시 강남구", StoreStatus.OPEN));

    given(jwtTokenProvider.validateToken(any())).willReturn(true);
    given(jwtTokenProvider.extractOwnerId(any())).willReturn(ownerId);

    mockMvc.perform(get("/api/owner/stores/me")
            .header("Authorization", "Bearer test-token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.storeId").value(storeId.toString()))
        .andExpect(jsonPath("$.name").value("테스트 식당"));
  }

  @Test
  void getMyStore_인증없음_403반환() throws Exception {
    mockMvc.perform(get("/api/owner/stores/me"))
        .andExpect(status().isForbidden());
  }

  @Test
  void getStoreSettings_인증된_점주_200반환() throws Exception {
    UUID ownerId = UUID.randomUUID();

    given(storeSettingsService.getSettings(eq(ownerId)))
        .willReturn(new StoreSettingsResponse(5, 30, null, null, 10, true, "대기 5팀 × 30분 / 5테이블"));

    given(jwtTokenProvider.validateToken(any())).willReturn(true);
    given(jwtTokenProvider.extractOwnerId(any())).willReturn(ownerId);

    mockMvc.perform(get("/api/owner/stores/me/settings")
            .header("Authorization", "Bearer test-token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tableCount").value(5))
        .andExpect(jsonPath("$.avgTurnoverMinutes").value(30));
  }
}
