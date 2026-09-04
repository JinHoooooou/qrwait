package com.qrwait.api.waiting.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;

@Getter
public class WaitingEntry {

  private static final int RETAINED_DIGITS = 4;

  private final UUID id;
  private final UUID storeId;
  private final String phoneNumber;
  private final int partySize;
  private final int waitingNumber;
  private final LocalDateTime createdAt;
  private final WaitingStatus status;
  private final LocalDate businessDate;
  private final LocalDateTime calledAt;
  private final LocalDateTime enteredAt;
  private final String phoneHash;

  private WaitingEntry(UUID id, UUID storeId, String phoneNumber, int partySize, int waitingNumber,
      WaitingStatus status, LocalDateTime createdAt, LocalDate businessDate,
      LocalDateTime calledAt, LocalDateTime enteredAt, String phoneHash) {
    this.id = id;
    this.storeId = storeId;
    this.phoneNumber = phoneNumber;
    this.partySize = partySize;
    this.waitingNumber = waitingNumber;
    this.status = status;
    this.createdAt = createdAt;
    this.businessDate = businessDate;
    this.calledAt = calledAt;
    this.enteredAt = enteredAt;
    this.phoneHash = phoneHash;
  }

  private WaitingEntry with(WaitingStatus newStatus, LocalDateTime newCalledAt,
      LocalDateTime newEnteredAt, String newPhoneNumber, String newPhoneHash) {
    return new WaitingEntry(id, storeId, newPhoneNumber, partySize, waitingNumber,
        newStatus, createdAt, businessDate, newCalledAt, newEnteredAt, newPhoneHash);
  }

  /**
   * 신규 웨이팅 등록
   */
  public static WaitingEntry create(UUID storeId, String phoneNumber, int partySize,
      int waitingNumber, LocalDate businessDate) {
    return new WaitingEntry(UUID.randomUUID(), storeId, phoneNumber, partySize, waitingNumber,
        WaitingStatus.WAITING, LocalDateTime.now(), businessDate, null, null, null);
  }

  /**
   * 영속 계층에서 복원
   */
  public static WaitingEntry restore(UUID id, UUID storeId, String phoneNumber, int partySize,
      int waitingNumber, WaitingStatus status, LocalDateTime createdAt,
      LocalDate businessDate, LocalDateTime calledAt, LocalDateTime enteredAt, String phoneHash) {
    return new WaitingEntry(id, storeId, phoneNumber, partySize, waitingNumber,
        status, createdAt, businessDate, calledAt, enteredAt, phoneHash);
  }

  // ===== 도메인 상태 전이 =====

  /**
   * WAITING → CALLED
   */
  public WaitingEntry call() {
    if (isPseudonymized()) {
      throw new IllegalStateException("가명처리된 웨이팅은 호출할 수 없습니다. 전체 전화번호가 없습니다.");
    }
    if (status != WaitingStatus.WAITING) {
      throw new IllegalStateException(
          "call() 은 WAITING 상태에서만 가능합니다. 현재 상태: " + status);
    }
    return with(WaitingStatus.CALLED, LocalDateTime.now(), enteredAt, phoneNumber, phoneHash);
  }

  /**
   * CALLED → ENTERED
   */
  public WaitingEntry enter() {
    if (status != WaitingStatus.CALLED) {
      throw new IllegalStateException(
          "enter() 는 CALLED 상태에서만 가능합니다. 현재 상태: " + status);
    }
    return with(WaitingStatus.ENTERED, calledAt, LocalDateTime.now(), phoneNumber, phoneHash);
  }

  /**
   * WAITING | CALLED → CANCELLED
   */
  public WaitingEntry cancel() {
    if (status != WaitingStatus.WAITING && status != WaitingStatus.CALLED) {
      throw new IllegalStateException(
          "cancel() 은 WAITING 또는 CALLED 상태에서만 가능합니다. 현재 상태: " + status);
    }
    return with(WaitingStatus.CANCELLED, calledAt, enteredAt, phoneNumber, phoneHash);
  }

  /**
   * CALLED → NO_SHOW
   */
  public WaitingEntry noShow() {
    if (status != WaitingStatus.CALLED) {
      throw new IllegalStateException(
          "noShow() 는 CALLED 상태에서만 가능합니다. 현재 상태: " + status);
    }
    return with(WaitingStatus.NO_SHOW, calledAt, enteredAt, phoneNumber, phoneHash);
  }

  /**
   * CALLED → WAITING. 호출을 취소하고 유예 타이머를 끈다. 번호와 순서는 그대로이므로
   * 다른 손님에게 영향이 없고, 점주는 다음 팀을 호출하면 된다.
   */
  public WaitingEntry postpone() {
    if (status != WaitingStatus.CALLED) {
      throw new IllegalStateException(
          "postpone() 은 CALLED 상태에서만 가능합니다. 현재 상태: " + status);
    }
    return with(WaitingStatus.WAITING, null, enteredAt, phoneNumber, phoneHash);
  }

  /**
   * 호출 유예가 지났는가. 상태가 아니라 calledAt 에서 파생되는 값이므로 감시 스케줄러가 필요 없다.
   */
  public boolean isGraceExpired(LocalDateTime now, int graceMinutes) {
    return status == WaitingStatus.CALLED
        && calledAt != null
        && calledAt.plusMinutes(graceMinutes).isBefore(now);
  }

  public boolean belongsTo(UUID storeId) {
    return this.storeId.equals(storeId);
  }

  /**
   * 가명처리 — 전체 번호를 뒤 4자리로 줄이고 해시를 심는다. 통계에 쓰이는 필드는 보존한다.
   * 해시 산출은 비밀키가 필요한 인프라 관심사이므로 계산된 값을 인자로 받는다.
   * 이미 가명처리된 항목에 다시 적용해도 뒤 4자리가 더 잘리지 않는다.
   */
  public WaitingEntry pseudonymize(String phoneHash) {
    String retained = phoneNumber.length() <= RETAINED_DIGITS
        ? phoneNumber
        : phoneNumber.substring(phoneNumber.length() - RETAINED_DIGITS);
    return with(status, calledAt, enteredAt, retained, phoneHash);
  }

  /**
   * 가명처리 여부 — {@code phoneHash != null} 로만 판정한다. 가명처리 배치가 대상 조회에 쓰는
   * {@code phoneHash IS NULL} 조건과 지금은 정확히 대응한다.
   *
   * <p><b>주의:</b> {@code phoneHash}는 오직 가명처리 배치({@code WaitingRetentionService})만
   * 채워야 한다. 훗날 "반복 노쇼 손님 식별" 기능을 등록 시점에 해시를 미리 채우는 방식으로
   * 구현하면 이 메서드의 의미가 깨진다 — 신규 항목이 전부 {@code isPseudonymized() == true}가
   * 되어 배치가 대상을 영원히 찾지 못하고(조용히 실패, 전체 번호가 다시는 가명처리되지 않음),
   * {@link #call()}은 모든 신규 등록에서 즉시 예외를 던진다. 등록 시점 해시가 필요해지면
   * 이 필드를 재사용하지 말고 별도 필드로 분리할 것.
   */
  public boolean isPseudonymized() {
    return phoneHash != null;
  }
}
