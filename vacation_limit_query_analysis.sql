-- VacationLimit 테이블의 현재 인덱스 구조 확인
SHOW INDEX FROM vacation_limits;

-- findByCompanyAndDateInAndRoleIn 쿼리의 실행 계획 분석
-- 실제 서비스에서 사용하는 쿼리
EXPLAIN ANALYZE
SELECT v.* 
FROM vacation_limits v 
WHERE v.company_id = 1
  AND v.date IN ('2025-01-01', '2025-01-02', '2025-01-03', '2025-01-04', '2025-01-05')
  AND v.role IN ('CAREGIVER', 'DRIVER', 'ALL');

-- 현재 인덱스 사용 상황 확인
EXPLAIN (ANALYZE, BUFFERS)
SELECT v.id, v.date, v.role, v.max_people, v.company_id
FROM vacation_limits v 
WHERE v.company_id = 1
  AND v.date IN ('2025-01-01', '2025-01-02', '2025-01-03', '2025-01-04', '2025-01-05')
  AND v.role IN ('CAREGIVER', 'DRIVER', 'ALL');

-- 커버링 인덱스 시뮬레이션
-- 만약 (company_id, date, role, max_people) 복합 인덱스가 있다면
CREATE INDEX idx_vacation_limits_covering 
ON vacation_limits(company_id, date, role) 
INCLUDE (max_people);

-- 커버링 인덱스 적용 후 실행 계획
EXPLAIN (ANALYZE, BUFFERS)
SELECT v.date, v.role, v.max_people
FROM vacation_limits v 
WHERE v.company_id = 1
  AND v.date IN ('2025-01-01', '2025-01-02', '2025-01-03', '2025-01-04', '2025-01-05')
  AND v.role IN ('CAREGIVER', 'DRIVER', 'ALL');

-- 현재 인덱스들의 카디널리티 확인
SELECT 
    indexname,
    tablename,
    pg_size_pretty(pg_relation_size(indexname::regclass)) as index_size,
    idx_scan,
    idx_tup_read,
    idx_tup_fetch
FROM pg_stat_user_indexes
WHERE tablename = 'vacation_limits';

-- 테이블 통계 정보
SELECT 
    n_distinct,
    correlation
FROM pg_stats
WHERE tablename = 'vacation_limits'
AND attname IN ('company_id', 'date', 'role');