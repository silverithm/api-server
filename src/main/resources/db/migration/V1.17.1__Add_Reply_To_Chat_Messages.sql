-- 채팅 메시지 답글(reply) 기능 추가
ALTER TABLE chat_messages ADD COLUMN reply_to_id BIGINT NULL;

-- 자기 참조 외래키
ALTER TABLE chat_messages ADD CONSTRAINT fk_chat_messages_reply_to
    FOREIGN KEY (reply_to_id) REFERENCES chat_messages(id) ON DELETE SET NULL;

-- 인덱스 추가
CREATE INDEX idx_chat_messages_reply_to_id ON chat_messages(reply_to_id);
