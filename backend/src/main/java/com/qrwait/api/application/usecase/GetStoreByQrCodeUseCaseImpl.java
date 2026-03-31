package com.qrwait.api.application.usecase;

import com.qrwait.api.application.dto.StoreResponse;
import com.qrwait.api.domain.model.Store;
import com.qrwait.api.domain.model.StoreNotFoundException;
import com.qrwait.api.domain.repository.StoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GetStoreByQrCodeUseCaseImpl implements GetStoreByQrCodeUseCase {

    private final StoreRepository storeRepository;

    @Override
    @Transactional(readOnly = true)
    public StoreResponse execute(String qrCode) {
        Store store = storeRepository.findByQrCode(qrCode)
                .orElseThrow(() -> new StoreNotFoundException(qrCode));

        return new StoreResponse(store.getId(), store.getName());
    }
}
