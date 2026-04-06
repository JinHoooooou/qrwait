package com.qrwait.api.presentation.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qrwait.api.application.dto.CreateStoreRequest;
import com.qrwait.api.application.dto.CreateStoreResponse;
import com.qrwait.api.application.dto.StoreResponse;
import com.qrwait.api.application.usecase.CreateStoreUseCase;
import com.qrwait.api.application.usecase.GenerateQrImageUseCase;
import com.qrwait.api.application.usecase.GetStoreByIdUseCase;
import com.qrwait.api.application.usecase.GetStoreWaitingStatusUseCase;
import com.qrwait.api.domain.model.StoreNotFoundException;
import com.qrwait.api.presentation.security.SecurityConfig;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(StoreController.class)
@Import(SecurityConfig.class)
class StoreControllerTest {

  @Autowired
  MockMvc mockMvc;
  @Autowired
  ObjectMapper objectMapper;
  @MockitoBean
  CreateStoreUseCase createStoreUseCase;
  @MockitoBean
  GenerateQrImageUseCase generateQrImageUseCase;
  @MockitoBean
  GetStoreByIdUseCase getStoreByIdUseCase;
  @MockitoBean
  GetStoreWaitingStatusUseCase getStoreWaitingStatusUseCase;

  @Test
  void createStore_성공_201반환() throws Exception {
    UUID storeId = UUID.randomUUID();
    CreateStoreRequest request = new CreateStoreRequest();
    request.setName("맛있는 식당");

    given(createStoreUseCase.execute(any(CreateStoreRequest.class)))
        .willReturn(new CreateStoreResponse(storeId, "맛있는 식당",
            "http://localhost:5173/wait?storeId=" + storeId));

    mockMvc.perform(post("/api/stores")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.storeId").value(storeId.toString()))
        .andExpect(jsonPath("$.name").value("맛있는 식당"))
        .andExpect(jsonPath("$.qrUrl").value("http://localhost:5173/wait?storeId=" + storeId));
  }

  @Test
  void createStore_이름누락_400반환() throws Exception {
    CreateStoreRequest request = new CreateStoreRequest();
    request.setName("");

    mockMvc.perform(post("/api/stores")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void getStore_존재하는_storeId_200반환() throws Exception {
    UUID storeId = UUID.randomUUID();
    given(getStoreByIdUseCase.execute(storeId))
        .willReturn(new StoreResponse(storeId, "테스트 식당"));

    mockMvc.perform(get("/api/stores/" + storeId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.storeId").value(storeId.toString()))
        .andExpect(jsonPath("$.name").value("테스트 식당"));
  }

  @Test
  void getStore_존재하지않는_storeId_404반환() throws Exception {
    UUID unknownId = UUID.randomUUID();
    given(getStoreByIdUseCase.execute(unknownId))
        .willThrow(new StoreNotFoundException(unknownId.toString()));

    mockMvc.perform(get("/api/stores/" + unknownId))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("STORE_NOT_FOUND"));
  }
}
