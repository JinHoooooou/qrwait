package com.qrwait.api.application.usecase;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.qrwait.api.domain.model.StoreNotFoundException;
import com.qrwait.api.domain.repository.StoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GenerateQrImageUseCaseImpl implements GenerateQrImageUseCase {

    private final StoreRepository storeRepository;

    @Value("${app.base-url}")
    private String baseUrl;

    @Override
    public byte[] execute(UUID storeId) {
        storeRepository.findById(storeId)
                .orElseThrow(() -> new StoreNotFoundException(storeId.toString()));

        String qrUrl = baseUrl + "/wait?storeId=" + storeId;

        try {
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix bitMatrix = writer.encode(qrUrl, BarcodeFormat.QR_CODE, 300, 300);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", out);
            return out.toByteArray();
        } catch (WriterException | IOException e) {
            throw new RuntimeException("QR 코드 생성 실패", e);
        }
    }
}
