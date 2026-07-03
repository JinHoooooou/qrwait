package com.qrwait.api.shared.sms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class NhnCloudSmsClientTest {

  private MockWebServer server;
  private NhnCloudSmsClient client;

  @BeforeEach
  void setUp() throws IOException {
    server = new MockWebServer();
    server.start();

    SmsProperties properties = new SmsProperties("APPKEY123", "SECRET456", "01099998888");
    RestClient restClient = RestClient.builder()
        .baseUrl(server.url("/").toString().replaceAll("/$", ""))
        .build();
    client = new NhnCloudSmsClient(properties, restClient);
  }

  @AfterEach
  void tearDown() throws IOException {
    server.shutdown();
  }

  @Test
  void send_성공_응답에서_예외를_던지지_않는다() throws InterruptedException {
    server.enqueue(new MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody("""
            {"header":{"isSuccessful":true,"resultCode":0,"resultMessage":"SUCCESS"}}
            """));

    client.send("010-1234-5678", "[QR Wait] 3번 손님, 홍콩반점에서 입장 안내드립니다. 매장으로 와주세요.");

    RecordedRequest request = server.takeRequest();
    assertThat(request.getPath()).isEqualTo("/sms/v3.0/appKeys/APPKEY123/sender/sms");
    assertThat(request.getHeader("X-Secret-Key")).isEqualTo("SECRET456");
    String body = request.getBody().readUtf8();
    assertThat(body).contains("\"sendNo\":\"01099998888\"");
    assertThat(body).contains("\"recipientNo\":\"01012345678\""); // 하이픈 제거
    assertThat(body).contains("3번 손님");
  }

  @Test
  void send_isSuccessful_false_이면_SmsSendException을_던진다() {
    server.enqueue(new MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody("""
            {"header":{"isSuccessful":false,"resultCode":-401,"resultMessage":"INVALID_SENDER"}}
            """));

    assertThatThrownBy(() -> client.send("010-1234-5678", "테스트"))
        .isInstanceOf(SmsSendException.class)
        .hasMessageContaining("INVALID_SENDER");
  }
}
