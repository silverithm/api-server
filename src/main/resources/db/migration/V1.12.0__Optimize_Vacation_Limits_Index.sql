-- 휴가 제한 테이블 인덱스 최적화
-- 쿼리 패턴: WHERE company_id = ? AND date IN (?, ?, ?) AND role IN (?, ?, ?)

-- 1. 최적화된 복합 인덱스 생성
-- company_id를 첫 번째로: 회사별 데이터 분리 (높은 선택도)
-- role을 두 번째로: 3개 값만 존재 (CAREGIVER, DRIVER, ALL)  
-- date를 마지막으로: IN 절 range scan 최적화
CREATE INDEX idx_vacation_limits_optimal ON vacation_limits(company_id, role, date);

-- 2. 기존 비효율적인 인덱스 제거
-- idx_vacation_limits_date_company 제거 (새 복합 인덱스로 대체됨)
ALTER TABLE vacation_limits DROP INDEX idx_vacation_limits_date_company;

-- 주의: idx_vacation_limits_company_id는 외래키 제약조건에 필요하므로 유지
-- 새로운 복합 인덱스가 company_id를 첫 번째 컬럼으로 포함하므로 쿼리 성능은 최적화됨

-- 3. 인덱스 통계 업데이트
ANALYZE TABLE vacation_limits;