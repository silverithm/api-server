-- 무료 구독 이력 테이블 생성
CREATE TABLE free_subscription_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    subscription_id BIGINT NOT NULL,
    created_at DATETIME(6),
    modified_at DATETIME(6),
    
    INDEX idx_free_subscription_history_user_id (user_id),
    FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE
);

-- 결제 실패 로그 테이블 생성
CREATE TABLE payment_failure_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    subscription_id BIGINT,
    failure_reason VARCHAR(255) NOT NULL,
    failure_message VARCHAR(1000),
    attempted_amount INTEGER,
    subscription_type VARCHAR(255),
    billing_type VARCHAR(255),
    payment_gateway_response VARCHAR(2000),
    created_at DATETIME(6),
    modified_at DATETIME(6),
    
    INDEX idx_payment_failure_log_user_id (user_id),
    INDEX idx_payment_failure_log_created_at (created_at),
    INDEX idx_payment_failure_log_failure_reason (failure_reason),
    FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE
);