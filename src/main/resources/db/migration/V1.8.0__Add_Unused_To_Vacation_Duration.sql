-- V1.8.0: VacationDuration enum에 UNUSED 값 추가
-- VacationRequest 테이블의 duration 컬럼에 새로운 enum 값 'UNUSED' 추가

-- duration 컬럼이 문자열로 저장되므로 스키마 변경은 불필요
-- Java enum에서 UNUSED 값을 추가했으므로 애플리케이션 레벨에서 처리

-- MySQL에서 컬럼에 주석 추가 (duration 값 설명)
ALTER TABLE vacation_requests 
MODIFY COLUMN duration VARCHAR(255) NOT NULL 
COMMENT 'Vacation duration type: FULL_DAY(연차), HALF_DAY_AM(오전 반차), HALF_DAY_PM(오후 반차), UNUSED(미사용)'; 