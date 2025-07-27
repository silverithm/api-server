-- MySQL 쿼리 실행 계획 분석

-- 1. 현재 인덱스 구조 확인
SHOW INDEX FROM vacation_limits;

-- 2. 테이블 통계 정보 확인
SHOW TABLE STATUS LIKE 'vacation_limits';

-- 3. 실제 서비스 쿼리의 실행 계획 분석
EXPLAIN ANALYZE
SELECT * FROM vacation_limits v
WHERE v.company_id = 1
  AND v.date IN ('2025-01-01', '2025-01-02', '2025-01-03', '2025-01-04', '2025-01-05')
  AND v.role IN ('CAREGIVER', 'DRIVER', 'ALL');

-- 4. JSON 형식으로 상세 실행 계획 확인
EXPLAIN FORMAT=JSON
SELECT * FROM vacation_limits v
WHERE v.company_id = 1
  AND v.date IN ('2025-01-01', '2025-01-02', '2025-01-03', '2025-01-04', '2025-01-05')
  AND v.role IN ('CAREGIVER', 'DRIVER', 'ALL');

-- 5. 인덱스 사용 통계 확인
SELECT 
    TABLE_NAME,
    INDEX_NAME,
    CARDINALITY,
    SEQ_IN_INDEX,
    COLUMN_NAME
FROM INFORMATION_SCHEMA.STATISTICS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'vacation_limits'
ORDER BY INDEX_NAME, SEQ_IN_INDEX;

-- 6. 실제 데이터 분포 확인
SELECT 
    COUNT(DISTINCT company_id) as distinct_companies,
    COUNT(DISTINCT date) as distinct_dates,
    COUNT(DISTINCT role) as distinct_roles,
    COUNT(*) as total_rows,
    COUNT(DISTINCT company_id) / COUNT(*) as company_selectivity,
    COUNT(DISTINCT date) / COUNT(*) as date_selectivity,
    COUNT(DISTINCT role) / COUNT(*) as role_selectivity
FROM vacation_limits;

-- 7. 인덱스별 실행 계획 비교
-- 7-1. FORCE INDEX로 각 인덱스 강제 사용
EXPLAIN 
SELECT * FROM vacation_limits FORCE INDEX (idx_vacation_limits_company_id)
WHERE company_id = 1
  AND date IN ('2025-01-01', '2025-01-02', '2025-01-03')
  AND role IN ('CAREGIVER', 'DRIVER');

EXPLAIN 
SELECT * FROM vacation_limits FORCE INDEX (idx_vacation_limits_date_company)
WHERE company_id = 1
  AND date IN ('2025-01-01', '2025-01-02', '2025-01-03')
  AND role IN ('CAREGIVER', 'DRIVER');

-- 8. 옵티마이저 트레이스 활성화 (상세 분석용)
SET optimizer_trace='enabled=on';

SELECT * FROM vacation_limits v
WHERE v.company_id = 1
  AND v.date IN ('2025-01-01', '2025-01-02', '2025-01-03')
  AND v.role IN ('CAREGIVER', 'DRIVER');

SELECT * FROM information_schema.OPTIMIZER_TRACE;

SET optimizer_trace='enabled=off';

-- 9. 인덱스 힌트를 사용한 성능 테스트
-- 실행 시간 측정
SET profiling = 1;

-- 원본 쿼리
SELECT SQL_NO_CACHE * FROM vacation_limits v
WHERE v.company_id = 1
  AND v.date IN ('2025-01-01', '2025-01-02', '2025-01-03')
  AND v.role IN ('CAREGIVER', 'DRIVER');

SHOW PROFILES;

-- 10. Handler 통계로 실제 I/O 확인
FLUSH STATUS;

SELECT * FROM vacation_limits v
WHERE v.company_id = 1
  AND v.date IN ('2025-01-01', '2025-01-02', '2025-01-03')
  AND v.role IN ('CAREGIVER', 'DRIVER');

SHOW STATUS LIKE 'Handler%';