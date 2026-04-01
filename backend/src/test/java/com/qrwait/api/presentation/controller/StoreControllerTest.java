package com.qrwait.api.presentation.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qrwait.api.application.dto.CreateStoreRequest;
import com.qrwait.api.application.dto.CreateStoreResponse;
import com.qrwait.api.application.dto.StoreResponse;
import com.qrwait.api.application.dto.WaitingStatusResponse;
import com.qrwait.api.application.usecase.CreateStoreUseCase;
import com.qrwait.api.application.usecase.GenerateQrImageUseCase;
import com.qrwait.api.application.usecase.GetStoreByQrCodeUseCase;
import com.qrwait.api.application.usecase.GetStoreWaitingStatusUseCase;
import com.qrwait.api.domain.model.StoreNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StoreController.class)
class StoreControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockitoBean CreateStoreUseCase createStoreUseCase;
    @MockitoBean GenerateQrImageUseCase generateQrImageUseCase;
    @MockitoBean GetStoreByQrCodeUseCase getStoreByQrCodeUseCase;
    @MockitoBean GetStoreWaitingStatusUseCase getStoreWaitingStatusUseCase;

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
    void getStore_존재하는_QR코드_200반환() throws Exception {
        UUID storeId = UUID.randomUUID();
        given(getStoreByQrCodeUseCase.execute("test-qr"))
                .willReturn(new StoreResponse(storeId, "테스트 식당"));

        mockMvc.perform(get("/api/stores/test-qr"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.storeId").value(storeId.toString()))
                .andExpect(jsonPath("$.name").value("테스트 식당"));
    }

    @Test
    void getStore_존재하지않는_QR코드_404반환() throws Exception {
        given(getStoreByQrCodeUseCase.execute("unknown-qr"))
                .willThrow(new StoreNotFoundException("unknown-qr"));

        mockMvc.perform(get("/api/stores/unknown-qr"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STORE_NOT_FOUND"));
    }
}
