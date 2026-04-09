package com.qrwait.api.owner.domain;

public class InvalidCredentialsException extends RuntimeException {

  public InvalidCredentialsException() {
    super("이메일 또는 비밀번호가 올바르지 않습니다.");
  }
}
