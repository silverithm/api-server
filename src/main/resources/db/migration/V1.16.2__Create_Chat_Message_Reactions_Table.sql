-- 채팅 메시지 이모지 리액션 테이블
CREATE TABLE IF NOT EXISTS chat_message_reactions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    message_id BIGINT NOT NULL,
    user_id VARCHAR(255) NOT NULL,
    user_name VARCHAR(255) NOT NULL,
    emoji VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_reaction_message FOREIGN KEY (message_id)
        REFERENCES chat_messages(id) ON DELETE CASCADE,

    -- 같은 사용자가 같은 메시지에 같은 이모지를 중복으로 못 남기게
    CONSTRAINT uk_message_user_emoji UNIQUE (message_id, user_id, emoji),

    INDEX idx_reaction_message_id (message_id),
    INDEX idx_reaction_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
