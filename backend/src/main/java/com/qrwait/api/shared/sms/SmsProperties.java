package com.qrwait.api.shared.sms;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "sms")
public record SmsProperties(
    @DefaultValue("") String appKey,
    @DefaultValue("") String secretKey,
    @DefaultValue("") String sender
) {

}
