package com.qrwait.api.shared.sms;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(SmsProperties.class)
public class SmsConfig {

  @Bean
  public SmsClient smsClient(SmsProperties smsProperties) {
    return new SolapiSmsClient(smsProperties, RestClient.create());
  }
}
