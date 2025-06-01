-- 알림 테이블
CREATE TABLE notifications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(255) NOT NULL COMMENT '알림 제목',
    message VARCHAR(1000) NOT NULL COMMENT '알림 메시지',
    recipient_token VARCHAR(500) NOT NULL COMMENT '수신자 FCM 토큰',
    recipient_user_id VARCHAR(100) COMMENT '수신자 사용자 ID',
    recipient_user_name VARCHAR(100) COMMENT '수신자 사용자 이름',
    type ENUM('VACATION_APPROVED', 'VACATION_REJECTED', 'VACATION_SUBMITTED', 'VACATION_REMINDER', 'GENERAL') NOT NULL DEFAULT 'GENERAL' COMMENT '알림 타입',
    related_entity_id BIGINT COMMENT '관련 엔티티 ID',
    related_entity_type VARCHAR(50) COMMENT '관련 엔티티 타입',
    sent BOOLEAN NOT NULL DEFAULT FALSE COMMENT '전송 여부',
    sent_at DATETIME COMMENT '전송 일시',
    fcm_message_id VARCHAR(255) COMMENT 'FCM 메시지 ID',
    error_message VARCHAR(1000) COMMENT '오류 메시지',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',
    
    INDEX idx_recipient_user_id (recipient_user_id),
    INDEX idx_recipient_user_name (recipient_user_name),
    INDEX idx_type (type),
    INDEX idx_sent (sent),
    INDEX idx_created_at (created_at),
    INDEX idx_related_entity (related_entity_id, related_entity_type)
) COMMENT='알림 정보 테이블'; 