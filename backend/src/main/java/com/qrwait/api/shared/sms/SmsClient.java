package com.qrwait.api.shared.sms;

public interface SmsClient {

  /**
   * @throws SmsSendException 발송 실패 (네트워크, provider 응답 실패 등)
   */
  void send(String to, String content);
}
