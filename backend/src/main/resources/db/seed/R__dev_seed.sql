-- 로컬 개발 전용 시드. application-local.yml 의 flyway.locations 에서만 로드된다.
-- Repeatable 마이그레이션이므로 재실행되어도 안전하도록 ON CONFLICT DO NOTHING 을 붙인다.
-- 계정: dev@qrwait.com / devpassword

INSERT INTO owners (id, email, password_hash, created_at)
VALUES ('00000000-0000-0000-0000-000000000000', 'dev@qrwait.com', '$2a$10$3iqU0lxaXyKUL5NBVVTZVeo.SMxAxxlNuIIjq6hFuBzy/zTYROfvm', now())
ON CONFLICT (id) DO NOTHING;

INSERT INTO stores (id, owner_id, name, created_at)
VALUES ('00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000000', '맛있는 한식당', now())
ON CONFLICT (id) DO NOTHING;

INSERT INTO store_settings (id, store_id)
VALUES ('00000000-0000-0000-0000-000000000011', '00000000-0000-0000-0000-000000000001')
ON CONFLICT (id) DO NOTHING;
