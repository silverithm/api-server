-- 알림 읽음 상태 컬럼 추가
ALTER TABLE notifications ADD COLUMN is_read BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE notifications ADD COLUMN read_at TIMESTAMP NULL;

-- 사용자별 읽지 않은 알림 조회 인덱스
CREATE INDEX idx_notifications_user_unread ON notifications (recipient_user_id, is_read);

-- 알림 타입에 CHAT, APPROVAL 추가 (enum 문자열이므로 별도 DDL 불필요, JPA @Enumerated(STRING) 사용)
