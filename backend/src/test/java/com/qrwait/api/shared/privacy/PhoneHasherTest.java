package com.qrwait.api.shared.privacy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PhoneHasherTest {

  private static final String SECRET = "test-phone-hash-secret-value";

  @Test
  void 같은_번호는_같은_해시를_만든다() {
    PhoneHasher hasher = new PhoneHasher(SECRET);

    assertThat(hasher.hash("010-1234-5678")).isEqualTo(hasher.hash("010-1234-5678"));
  }

  @Test
  void 다른_번호는_다른_해시를_만든다() {
    PhoneHasher hasher = new PhoneHasher(SECRET);

    assertThat(hasher.hash("010-1234-5678")).isNotEqualTo(hasher.hash("010-1234-5679"));
  }

  @Test
  void 키가_다르면_같은_번호도_다른_해시가_된다() {
    // 키를 모르면 대조표를 만들 수 없다는 것이 HMAC 을 쓰는 이유다.
    assertThat(new PhoneHasher(SECRET).hash("010-1234-5678"))
        .isNotEqualTo(new PhoneHasher("another-secret").hash("010-1234-5678"));
  }

  @Test
  void 해시는_64자_hex다() {
    assertThat(new PhoneHasher(SECRET).hash("010-1234-5678"))
        .hasSize(64)
        .matches("[0-9a-f]{64}");
  }
}
