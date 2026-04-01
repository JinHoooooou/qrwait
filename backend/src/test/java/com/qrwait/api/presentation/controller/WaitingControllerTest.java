package com.qrwait.api.presentation.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qrwait.api.application.dto.RegisterWaitingRequest;
import com.qrwait.api.application.dto.RegisterWaitingResponse;
import com.qrwait.api.application.usecase.CancelWaitingUseCase;
import com.qrwait.api.application.usecase.EnterWaitingUseCaseImpl;
import com.qrwait.api.application.usecase.GetWaitingStatusUseCase;
import com.qrwait.api.application.usecase.RegisterWaitingUseCase;
import com.qrwait.api.infrastructure.sse.WaitingSseService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(WaitingController.class)
class WaitingControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockitoBean RegisterWaitingUseCase registerWaitingUseCase;
    @MockitoBean GetWaitingStatusUseCase getWaitingStatusUseCase;
    @MockitoBean CancelWaitingUseCase cancelWaitingUseCase;
  @MockitoBean
  EnterWaitingUseCaseImpl enterWaitingUseCase;
  @MockitoBean
  WaitingSseService waitingSseService;

    @Test
    void register_성공_201반환_응답바디_검증() throws Exception {
        UUID storeId = UUID.randomUUID();
        UUID waitingId = UUID.randomUUID();

        given(registerWaitingUseCase.execute(eq(storeId), any()))
                .willReturn(new RegisterWaitingResponse(waitingId, 3, 3, 3, 15, "token-abc"));

        RegisterWaitingRequest request = new RegisterWaitingRequest();
        request.setVisitorName("홍길동");
        request.setPartySize(2);

        mockMvc.perform(post("/api/stores/{storeId}/waitings", storeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.waitingNumber").value(3))
                .andExpect(jsonPath("$.currentRank").value(3))
                .andExpect(jsonPath("$.waitingToken").value("token-abc"));
    }

    @Test
    void register_partySizeZero_400반환() throws Exception {
        UUID storeId = UUID.randomUUID();

        RegisterWaitingRequest request = new RegisterWaitingRequest();
        request.setVisitorName("홍길동");
        request.setPartySize(0);

        mockMvc.perform(post("/api/stores/{storeId}/waitings", storeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }
}
