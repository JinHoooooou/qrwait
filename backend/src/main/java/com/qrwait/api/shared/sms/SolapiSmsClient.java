package com.qrwait.api.shared.sms;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.RestClient;

@Slf4j
@RequiredArgsConstructor
public class SolapiSmsClient implements SmsClient {

  private static final String SOLAPI_URL = "https://api.solapi.com/messages/v4/send";

  private final SmsProperties smsProperties;
  private final RestClient restClient;

  @Override
  public void send(String to, String content) {
    String date = Instant.now().toString();
    String salt = UUID.randomUUID().toString().replace("-", "");
    String signature = hmacSha256(smsProperties.apiSecret(), date + salt);

    String authorization = "HMAC-SHA256 apiKey=%s, date=%s, salt=%s, signature=%s"
        .formatted(smsProperties.apiKey(), date, salt, signature);

    Map<String, Object> body = Map.of(
        "message", Map.of(
            "to", to.replace("-", ""),
            "from", smsProperties.sender().replace("-", ""),
            "text", content
        )
    );

    restClient.post()
        .uri(SOLAPI_URL)
        .header("Authorization", authorization)
        .body(body)
        .retrieve()
        .toBodilessEntity();

    log.info("SMS 발송 성공: to={}", to);
  }

  private String hmacSha256(String secret, String data) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (Exception e) {
      throw new RuntimeException("HMAC-SHA256 서명 생성 실패", e);
    }
  }
}
