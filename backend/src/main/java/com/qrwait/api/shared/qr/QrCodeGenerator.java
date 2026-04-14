package com.qrwait.api.shared.qr;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.springframework.stereotype.Component;

@Component
public class QrCodeGenerator {

  private static final int SIZE = 300;

  public byte[] generate(String url) {
    try {
      QRCodeWriter writer = new QRCodeWriter();
      BitMatrix bitMatrix = writer.encode(url, BarcodeFormat.QR_CODE, SIZE, SIZE);
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      MatrixToImageWriter.writeToStream(bitMatrix, "PNG", out);
      return out.toByteArray();
    } catch (WriterException | IOException e) {
      throw new QrCodeGenerationException("QR 코드 생성 실패: " + url, e);
    }
  }
}
