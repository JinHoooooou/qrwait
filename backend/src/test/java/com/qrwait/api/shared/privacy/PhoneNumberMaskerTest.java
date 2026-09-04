package com.qrwait.api.shared.privacy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PhoneNumberMaskerTest {

  @Test
  void 전체_번호는_뒤_4자리만_남긴다() {
    assertThat(PhoneNumberMasker.mask("010-1234-5678")).isEqualTo("****-5678");
  }

  @Test
  void 이미_가명처리된_뒤_4자리도_같은_형태로_렌더된다() {
    // 가명처리 전후로 화면 표시가 같아야 한다.
    assertThat(PhoneNumberMasker.mask("5678")).isEqualTo("****-5678");
  }

  @Test
  void 값이_없거나_짧으면_별표만() {
    assertThat(PhoneNumberMasker.mask(null)).isEqualTo("****");
    assertThat(PhoneNumberMasker.mask("12")).isEqualTo("****");
  }
}
