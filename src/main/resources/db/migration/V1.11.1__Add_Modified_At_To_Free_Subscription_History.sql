-- free_subscription_history 테이블에 modified_at 컬럼 추가 (컬럼이 없는 경우에만)
SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS 
                   WHERE TABLE_SCHEMA = DATABASE() 
                   AND TABLE_NAME = 'free_subscription_history' 
                   AND COLUMN_NAME = 'modified_at');

SET @sql = IF(@col_exists = 0, 
              'ALTER TABLE free_subscription_history ADD COLUMN modified_at DATETIME(6)', 
              'SELECT "Column modified_at already exists in free_subscription_history" as message');

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- payment_failure_log 테이블에 modified_at 컬럼 추가 (컬럼이 없는 경우에만)
SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS 
                   WHERE TABLE_SCHEMA = DATABASE() 
                   AND TABLE_NAME = 'payment_failure_log' 
                   AND COLUMN_NAME = 'modified_at');

SET @sql = IF(@col_exists = 0, 
              'ALTER TABLE payment_failure_log ADD COLUMN modified_at DATETIME(6)', 
              'SELECT "Column modified_at already exists in payment_failure_log" as message');

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;