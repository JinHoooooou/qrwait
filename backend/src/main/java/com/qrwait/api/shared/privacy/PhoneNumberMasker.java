package com.qrwait.api.shared.privacy;

/**
 * 표시용 전화번호 마스킹. 저장값이 전체 번호든 가명처리된 뒤 4자리든 출력이 같으므로,
 * 해당 행이 가명처리되었는지가 화면에 드러나지 않는다.
 */
public final class PhoneNumberMasker {

  private static final int VISIBLE_DIGITS = 4;

  private PhoneNumberMasker() {
  }

  public static String mask(String phoneNumber) {
    if (phoneNumber == null || phoneNumber.length() < VISIBLE_DIGITS) {
      return "****";
    }
    return "****-" + phoneNumber.substring(phoneNumber.length() - VISIBLE_DIGITS);
  }
}
