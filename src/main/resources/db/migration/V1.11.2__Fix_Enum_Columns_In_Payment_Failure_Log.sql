-- payment_failure_log 테이블의 enum 컬럼들을 올바른 타입으로 변경

-- billing_type 컬럼을 ENUM으로 변경
ALTER TABLE payment_failure_log 
MODIFY COLUMN billing_type ENUM('FREE', 'MONTHLY', 'YEARLY');

-- subscription_type 컬럼도 ENUM으로 변경
ALTER TABLE payment_failure_log 
MODIFY COLUMN subscription_type ENUM('BASIC', 'ENTERPRISE', 'FREE');

-- failure_reason 컬럼도 ENUM으로 변경
ALTER TABLE payment_failure_log 
MODIFY COLUMN failure_reason ENUM('CARD_LIMIT_EXCEEDED', 'CARD_SUSPENDED', 'INSUFFICIENT_BALANCE', 'INVALID_CARD', 'EXPIRED_CARD', 'NETWORK_ERROR', 'PAYMENT_GATEWAY_ERROR', 'OTHER');