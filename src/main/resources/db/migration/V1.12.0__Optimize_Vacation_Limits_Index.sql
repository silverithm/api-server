-- 휴가 제한 테이블 인덱스 최적화
-- 쿼리 패턴: WHERE company_id = ? AND date IN (?, ?, ?) AND role IN (?, ?, ?)

-- 1. 최적화된 복합 인덱스 생성 (이미 엔티티에 정의된 경우 스킵)
-- company_id를 첫 번째로: 회사별 데이터 분리 (높은 선택도)
-- role을 두 번째로: 3개 값만 존재 (CAREGIVER, DRIVER, ALL)
-- date를 마지막으로: IN 절 range scan 최적화
CREATE INDEX IF NOT EXISTS idx_vacation_limits_optimal 
ON vacation_limits(company_id, role, date);

-- 2. 기존 중복/비효율 인덱스 제거
-- 주의: company_id 단일 인덱스는 외래키 제약조건에 필요하므로 새 복합 인덱스 생성 후 제거
ALTER TABLE vacation_limits DROP INDEX idx_vacation_limits_date_company;
ALTER TABLE vacation_limits DROP INDEX idx_vacation_limits_company_id;

-- 3. 유니크 제약조건 유지 (데이터 무결성)
-- 기존 유니크 제약조건은 그대로 유지 (date, role, company_id)
-- 비즈니스 로직상 같은 날짜, 같은 역할, 같은 회사에 중복 제한 방지

-- 4. 인덱스 통계 업데이트
ANALYZE TABLE vacation_limits;