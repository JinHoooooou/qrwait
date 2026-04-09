package com.qrwait.api.domain.model;

public class StoreNotAvailableException extends RuntimeException {

  public StoreNotAvailableException(StoreStatus status) {
    super("매장이 현재 웨이팅을 받지 않습니다. 현재 상태: " + status);
  }
}
