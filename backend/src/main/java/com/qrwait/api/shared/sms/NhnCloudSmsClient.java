package com.qrwait.api.shared.sms;

import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@RequiredArgsConstructor
public class NhnCloudSmsClient implements SmsClient {

  private static final String PATH_TEMPLATE = "/sms/v3.0/appKeys/{appKey}/sender/sms";

  private final SmsProperties properties;
  private final RestClient restClient;

  @Override
  public void send(String to, String content) {
    Map<String, Object> body = Map.of(
        "body", content,
        "sendNo", properties.sender().replace("-", ""),
        "recipientList", List.of(Map.of("recipientNo", to.replace("-", "")))
    );

    NhnSmsResponse response;
    try {
      response = restClient.post()
          .uri(PATH_TEMPLATE, properties.appKey())
          .header("X-Secret-Key", properties.secretKey())
          .contentType(MediaType.APPLICATION_JSON)
          .body(body)
          .retrieve()
          .body(NhnSmsResponse.class);
    } catch (RestClientException e) {
      throw new SmsSendException("NHN Cloud SMS 호출 실패", e);
    }

    if (response == null || response.header() == null || !response.header().isSuccessful()) {
      String reason = (response == null || response.header() == null)
          ? "empty response"
          : response.header().resultMessage();
      throw new SmsSendException("NHN Cloud SMS 발송 실패: " + reason);
    }

    log.info("SMS 발송 성공: to={}", to);
  }

  private record NhnSmsResponse(Header header) {

    private record Header(boolean isSuccessful, int resultCode, String resultMessage) {

    }
  }
}
