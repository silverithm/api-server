-- V1.7.2: Remove unique constraints from username and email for multi-company support

-- members 테이블에서 unique 제약조건 제거
ALTER TABLE members DROP INDEX username;
ALTER TABLE members DROP INDEX email;

-- member_join_requests 테이블에서 unique 제약조건 제거
ALTER TABLE member_join_requests DROP INDEX username;
ALTER TABLE member_join_requests DROP INDEX email;

-- 회사별 복합 unique 제약조건 추가 (선택사항)
-- 같은 회사 내에서는 username과 email이 유일해야 함
ALTER TABLE members ADD CONSTRAINT uk_members_company_username UNIQUE (company_id, username);
ALTER TABLE members ADD CONSTRAINT uk_members_company_email UNIQUE (company_id, email);

ALTER TABLE member_join_requests ADD CONSTRAINT uk_member_join_requests_company_username UNIQUE (company_id, username);
ALTER TABLE member_join_requests ADD CONSTRAINT uk_member_join_requests_company_email UNIQUE (company_id, email); 