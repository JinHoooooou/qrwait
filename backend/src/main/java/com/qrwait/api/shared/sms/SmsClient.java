package com.qrwait.api.shared.sms;

public interface SmsClient {

  void send(String to, String content);
}
