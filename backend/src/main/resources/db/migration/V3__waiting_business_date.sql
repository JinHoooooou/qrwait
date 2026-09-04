-- 영업일 기준 채번·통계·가명처리를 위한 컬럼과 제약.
ALTER TABLE waiting_entries
  ADD COLUMN business_date DATE,
  ADD COLUMN called_at     TIMESTAMP,
  ADD COLUMN entered_at    TIMESTAMP,
  ADD COLUMN phone_hash    VARCHAR(64);

-- 기존 행 backfill. 실서비스 데이터는 없으나 로컬/개발 DB를 위한 안전장치.
-- 기본 영업일 시작(05:00)을 가정한다.
UPDATE waiting_entries
   SET business_date = (created_at - INTERVAL '5 hours')::date
 WHERE business_date IS NULL;

ALTER TABLE waiting_entries ALTER COLUMN business_date SET NOT NULL;

-- phone_number 는 NOT NULL 을 유지한다. 가명처리 후에도 뒤 4자리가 들어간다.
ALTER TABLE waiting_entries
  ADD CONSTRAINT uq_waiting_store_business_date_number
  UNIQUE (store_id, business_date, waiting_number);
