package com.qrwait.api.waiting.domain;

import java.util.UUID;

public class WaitingNumberConflictException extends RuntimeException {

  public WaitingNumberConflictException(UUID storeId, Throwable cause) {
    super("대기번호 채번에 반복 실패했습니다. storeId=" + storeId, cause);
  }
}
