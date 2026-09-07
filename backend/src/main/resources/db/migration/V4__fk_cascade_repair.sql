-- 2026-04-23 최초 배포 당시 V1__init.sql 에는 ON DELETE CASCADE 가 없었다.
-- 이후 V1 파일이 수정되며 CASCADE 가 추가됐지만(체크섬 변경), 이미 V1을 적용한 환경의
-- 실제 스키마는 갱신되지 않는다. 이 마이그레이션이 그 간극을 메운다.
--
-- 신규 환경(V1을 방금 적용한 경우)에서는 FK가 이미 CASCADE 상태이므로, 동일한 제약을
-- 다시 만드는 것뿐이라 안전하다(no-op와 동일한 효과).
ALTER TABLE stores DROP CONSTRAINT stores_owner_id_fkey;
ALTER TABLE stores
  ADD CONSTRAINT stores_owner_id_fkey FOREIGN KEY (owner_id) REFERENCES owners (id) ON DELETE CASCADE;

ALTER TABLE waiting_entries DROP CONSTRAINT waiting_entries_store_id_fkey;
ALTER TABLE waiting_entries
  ADD CONSTRAINT waiting_entries_store_id_fkey FOREIGN KEY (store_id) REFERENCES stores (id) ON DELETE CASCADE;

ALTER TABLE store_settings DROP CONSTRAINT store_settings_store_id_fkey;
ALTER TABLE store_settings
  ADD CONSTRAINT store_settings_store_id_fkey FOREIGN KEY (store_id) REFERENCES stores (id) ON DELETE CASCADE;
