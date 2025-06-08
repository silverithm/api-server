-- V1.7.6: duration 컬럼 타입을 일관성 있게 VARCHAR로 설정

-- 기존 duration 컬럼이 있으면 제거
SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS 
     WHERE TABLE_SCHEMA = DATABASE() 
     AND TABLE_NAME = 'vacation_requests' 
     AND COLUMN_NAME = 'duration') > 0,
    'ALTER TABLE vacation_requests DROP COLUMN duration',
    'SELECT 1'
));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- VARCHAR 타입으로 duration 컬럼 추가
ALTER TABLE vacation_requests 
ADD COLUMN duration VARCHAR(50) NOT NULL DEFAULT 'FULL_DAY' COMMENT '휴무 기간 (FULL_DAY: 연차, HALF_DAY_AM: 오전 반차, HALF_DAY_PM: 오후 반차)'; 