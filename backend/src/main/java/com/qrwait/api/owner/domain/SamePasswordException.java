package com.qrwait.api.owner.domain;

public class SamePasswordException extends RuntimeException {

  public SamePasswordException() {
    super("새 비밀번호는 현재 비밀번호와 달라야 합니다.");
  }
}
