package com.qrwait.api.shared.sms;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(SmsProperties.class)
public class SmsConfig {

  private static final String NHN_CLOUD_SMS_BASE_URL = "https://sms.api.nhncloudservice.com";
  private static final int TIMEOUT_MS = 3_000;

  @Bean
  public SmsClient smsClient(SmsProperties properties) {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(TIMEOUT_MS);
    factory.setReadTimeout(TIMEOUT_MS);

    RestClient restClient = RestClient.builder()
        .baseUrl(NHN_CLOUD_SMS_BASE_URL)
        .requestFactory(factory)
        .build();

    return new NhnCloudSmsClient(properties, restClient);
  }
}
