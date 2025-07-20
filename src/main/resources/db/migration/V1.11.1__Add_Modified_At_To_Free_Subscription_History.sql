-- free_subscription_history 테이블에 modified_at 컬럼 추가
ALTER TABLE free_subscription_history 
ADD COLUMN IF NOT EXISTS modified_at DATETIME(6);

-- payment_failure_log 테이블에 modified_at 컬럼 추가 (혹시 없는 경우)
ALTER TABLE payment_failure_log 
ADD COLUMN IF NOT EXISTS modified_at DATETIME(6);