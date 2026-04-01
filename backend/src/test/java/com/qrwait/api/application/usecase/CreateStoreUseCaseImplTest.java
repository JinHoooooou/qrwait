package com.qrwait.api.application.usecase;

import com.qrwait.api.application.dto.CreateStoreRequest;
import com.qrwait.api.application.dto.CreateStoreResponse;
import com.qrwait.api.domain.model.Store;
import com.qrwait.api.domain.repository.StoreRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class CreateStoreUseCaseImplTest {

    @Mock StoreRepository storeRepository;
    @InjectMocks CreateStoreUseCaseImpl useCase;

    @Test
    void execute_정상등록_storeId_name_qrUrl_반환() {
        // given
        ReflectionTestUtils.setField(useCase, "baseUrl", "http://localhost:5173");

        Store store = Store.create("맛있는 식당");
        given(storeRepository.save(any(Store.class))).willReturn(store);

        CreateStoreRequest request = new CreateStoreRequest();
        request.setName("맛있는 식당");

        // when
        CreateStoreResponse response = useCase.execute(request);

        // then
        assertThat(response.storeId()).isEqualTo(store.getId());
        assertThat(response.name()).isEqualTo("맛있는 식당");
        assertThat(response.qrUrl())
                .startsWith("http://localhost:5173/wait?storeId=")
                .contains(store.getId().toString());
    }
}
