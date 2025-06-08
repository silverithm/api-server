-- V1.7.4: vacation_requests 테이블에서 password 컬럼 제거
-- 휴가 시스템에서 password 기반 인증을 userId + userName 기반 인증으로 변경

-- vacation_requests 테이블에서 password 컬럼 제거
ALTER TABLE vacation_requests DROP COLUMN password; 