package com.qrwait.api.shared.privacy;

import java.nio.charset.StandardCharsets;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 전화번호의 HMAC-SHA256 해시. 반복 노쇼 손님을 식별하기 위한 가명 식별자다.
 *
 * <p>솔트 없는 단순 해시를 쓰지 않는 이유는 전화번호의 경우의 수가 10^8 밖에 되지 않아
 * 전수 대입으로 즉시 역산되기 때문이다. 비밀키를 쓰면 키를 모르는 쪽에서는 대조표를 만들 수 없다.
 *
 * <p>키를 교체하면 기존 해시와 매칭이 끊긴다.
 */
@Component
public class PhoneHasher {

  private static final String ALGORITHM = "HmacSHA256";

  private final SecretKeySpec key;

  public PhoneHasher(@Value("${privacy.phone-hash-secret}") String secret) {
    if (secret == null || secret.isBlank()) {
      throw new IllegalStateException("privacy.phone-hash-secret 이 설정되지 않았습니다.");
    }
    this.key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM);
  }

  public String hash(String phoneNumber) {
    try {
      Mac mac = Mac.getInstance(ALGORITHM);
      mac.init(key);
      byte[] digest = mac.doFinal(phoneNumber.getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder(digest.length * 2);
      for (byte b : digest) {
        hex.append("%02x".formatted(b));
      }
      return hex.toString();
    } catch (java.security.GeneralSecurityException e) {
      throw new IllegalStateException("전화번호 해시 산출에 실패했습니다.", e);
    }
  }
}
