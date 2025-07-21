-- Add is_scheduled_payment column to payment_failure_log table
-- This column distinguishes between scheduled payment failures and manual payment failures

ALTER TABLE payment_failure_log 
ADD COLUMN is_scheduled_payment BOOLEAN DEFAULT FALSE NOT NULL;

-- Add index for better query performance when filtering by scheduled payment status
CREATE INDEX idx_payment_failure_log_user_reason_scheduled_created 
ON payment_failure_log(user_id, failure_reason, is_scheduled_payment, created_at);

-- Add comment for documentation
COMMENT ON COLUMN payment_failure_log.is_scheduled_payment IS 'TRUE if this failure occurred during scheduled payment processing, FALSE for manual payment failures';