-- 영업일 경계와 호출 유예 시간. 채번·통계·가명처리가 공통으로 쓰는 기준이다.
ALTER TABLE store_settings
  ADD COLUMN business_day_start TIME NOT NULL DEFAULT '05:00',
  ADD COLUMN call_grace_minutes INT  NOT NULL DEFAULT 5;
