-- 휴가 제한 테이블 인덱스 최적화
-- 쿼리 패턴: WHERE company_id = ? AND date IN (?, ?, ?) AND role IN (?, ?, ?)

-- 1. 기존 중복/비효율 인덱스 제거
DROP INDEX IF EXISTS idx_vacation_limits_company_id;
DROP INDEX IF EXISTS idx_vacation_limits_date_company;

-- 2. 최적화된 복합 인덱스 생성
-- company_id를 첫 번째로: 회사별 데이터 분리 (높은 선택도)
-- role을 두 번째로: 3개 값만 존재 (CAREGIVER, DRIVER, ALL)
-- date를 마지막으로: IN 절 range scan 최적화
CREATE INDEX idx_vacation_limits_optimal 
ON vacation_limits(company_id, role, date);

-- 3. 인덱스 통계 업데이트
ANALYZE TABLE vacation_limits;