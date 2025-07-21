-- Add is_scheduled_payment column to payment_failure_log table if it doesn't exist
-- This column distinguishes between scheduled payment failures and manual payment failures

-- Use stored procedure approach for better MySQL compatibility
DELIMITER //

CREATE PROCEDURE AddScheduledPaymentColumn()
BEGIN
    DECLARE column_count INT DEFAULT 0;
    DECLARE index_count INT DEFAULT 0;
    
    -- Check if column exists
    SELECT COUNT(*) INTO column_count
    FROM information_schema.COLUMNS 
    WHERE TABLE_SCHEMA = DATABASE() 
    AND TABLE_NAME = 'payment_failure_log' 
    AND COLUMN_NAME = 'is_scheduled_payment';
    
    -- Add column if it doesn't exist
    IF column_count = 0 THEN
        ALTER TABLE payment_failure_log 
        ADD COLUMN is_scheduled_payment BOOLEAN DEFAULT FALSE NOT NULL;
    END IF;
    
    -- Check if index exists
    SELECT COUNT(*) INTO index_count
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'payment_failure_log'
    AND INDEX_NAME = 'idx_payment_failure_log_user_reason_scheduled_created';
    
    -- Add index if it doesn't exist
    IF index_count = 0 THEN
        CREATE INDEX idx_payment_failure_log_user_reason_scheduled_created 
        ON payment_failure_log(user_id, failure_reason, is_scheduled_payment, created_at);
    END IF;
    
END //

DELIMITER ;

-- Execute the procedure
CALL AddScheduledPaymentColumn();

-- Drop the procedure after use
DROP PROCEDURE AddScheduledPaymentColumn;