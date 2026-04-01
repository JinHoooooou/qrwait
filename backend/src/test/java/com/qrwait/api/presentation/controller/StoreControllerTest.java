package com.qrwait.api.presentation.controller;

import com.qrwait.api.application.dto.StoreResponse;
import com.qrwait.api.application.dto.WaitingStatusResponse;
import com.qrwait.api.application.usecase.CreateStoreUseCase;
import com.qrwait.api.application.usecase.GetStoreByQrCodeUseCase;
import com.qrwait.api.application.usecase.GetStoreWaitingStatusUseCase;
import com.qrwait.api.domain.model.StoreNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StoreController.class)
class StoreControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean CreateStoreUseCase createStoreUseCase;
    @MockitoBean GetStoreByQrCodeUseCase getStoreByQrCodeUseCase;
    @MockitoBean GetStoreWaitingStatusUseCase getStoreWaitingStatusUseCase;

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
