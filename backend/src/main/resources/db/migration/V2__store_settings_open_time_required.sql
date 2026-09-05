-- 영업 시작 시각(open_time)을 영업일 경계로 재사용한다. 채번·통계·가명처리가 공통으로 쓰는
-- 기준선이므로 필수값으로 만든다. 마감 시각(close_time)이 아니라 시작 시각을 경계로 쓰는 이유는
-- StoreSettings.businessDateOf() 참고 — 마감 시각을 쓰면 정상 영업시간 전체가 전날로 잘못 귀속된다.
UPDATE store_settings SET open_time = '05:00' WHERE open_time IS NULL;

ALTER TABLE store_settings
  ALTER COLUMN open_time SET NOT NULL,
  ALTER COLUMN open_time SET DEFAULT '05:00',
  ADD COLUMN call_grace_minutes INT NOT NULL DEFAULT 5;
