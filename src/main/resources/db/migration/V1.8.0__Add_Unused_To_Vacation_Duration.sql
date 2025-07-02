-- V1.8.0: VacationDuration enum에 UNUSED 값 추가
-- VacationRequest 테이블의 duration 컬럼에 새로운 enum 값 'UNUSED' 추가

-- duration 컬럼이 문자열로 저장되므로 스키마 변경은 불필요하지만,
-- 데이터 무결성을 위해 체크 제약조건을 업데이트

-- 기존 체크 제약조건 제거 (있는 경우)
ALTER TABLE vacation_requests DROP CONSTRAINT IF EXISTS chk_vacation_duration;

-- 새로운 체크 제약조건 추가 (UNUSED 포함)
ALTER TABLE vacation_requests 
ADD CONSTRAINT chk_vacation_duration 
CHECK (duration IN ('FULL_DAY', 'HALF_DAY_AM', 'HALF_DAY_PM', 'UNUSED'));

-- 테이블에 주석 추가
COMMENT ON COLUMN vacation_requests.duration IS 'Vacation duration type: FULL_DAY(연차), HALF_DAY_AM(오전 반차), HALF_DAY_PM(오후 반차), UNUSED(미사용)'; 