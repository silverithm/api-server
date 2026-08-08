-- 중복 인덱스 정리
--
-- 같은 컬럼에 인덱스가 두 개 걸려 있거나, 이미 다른 복합 인덱스의 왼쪽 접두사라서
-- 옵티마이저가 절대 고르지 않는 것들을 지운다. 읽기에는 도움이 안 되면서 INSERT/UPDATE마다
-- 갱신 비용만 물고 있었다 — vacation_requests는 인덱스가 데이터의 4.5배까지 불어 있었다.
--
-- 지우는 인덱스는 모두 아래 둘 중 하나다:
--   (a) 완전히 같은 컬럼의 인덱스가 따로 있다
--   (b) 남는 복합 인덱스의 왼쪽 접두사다 (B-Tree는 접두사 조회를 그대로 처리한다)
--
-- FK가 걸린 컬럼의 인덱스(chat_messages.chat_room_id, schedules.company_id)는 손대지 않았다.
-- 접두사를 제공하는 복합 인덱스가 남아 있어 이론상 지울 수 있지만, 거부되면 마이그레이션이
-- 실패로 박제돼 이후 배포까지 막힌다. 두 테이블 모두 300행 남짓이라 얻을 것도 거의 없다.

-- (a) 같은 컬럼에 이름만 다른 인덱스가 둘씩 있던 것
ALTER TABLE members DROP INDEX idx_email;        -- = idx_members_email(email)
ALTER TABLE members DROP INDEX idx_username;     -- = idx_members_username(username)

-- (b) 복합 인덱스의 왼쪽 접두사
ALTER TABLE notifications DROP INDEX idx_recipient_user_id;
-- ⊂ idx_notifications_user_unread(recipient_user_id, is_read)

ALTER TABLE vacation_requests DROP INDEX idx_date;
-- ⊂ idx_date_role(date, role)
ALTER TABLE vacation_requests DROP INDEX idx_role;
-- ⊂ idx_vacation_requests_role_company(role, company_id)

ALTER TABLE vacation_limits DROP INDEX idx_date;
-- ⊂ uk_date_role_company(date, role, company_id)
