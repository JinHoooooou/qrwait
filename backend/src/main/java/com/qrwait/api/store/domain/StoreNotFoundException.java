package com.qrwait.api.store.domain;

import java.util.UUID;

public class StoreNotFoundException extends RuntimeException {

  public StoreNotFoundException(UUID storeId) {
    super("매장을 찾을 수 없습니다. storeId=" + storeId);
  }

  public StoreNotFoundException(String message) {
    super("매장을 찾을 수 없습니다. " + message);
  }
}
