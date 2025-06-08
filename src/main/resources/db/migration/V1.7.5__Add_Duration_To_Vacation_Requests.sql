-- V1.7.5: vacation_requests 테이블에 duration 컬럼 추가
-- 연차/반차 구분을 위한 duration 필드 추가

-- vacation_requests 테이블에 duration 컬럼 추가
ALTER TABLE vacation_requests 
ADD COLUMN duration VARCHAR(50) NOT NULL DEFAULT 'FULL_DAY' COMMENT '휴무 기간 (FULL_DAY: 연차, HALF_DAY_AM: 오전 반차, HALF_DAY_PM: 오후 반차)'; 