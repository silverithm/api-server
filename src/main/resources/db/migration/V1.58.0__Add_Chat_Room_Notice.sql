-- 채팅방 공지 — 메시지 하나를 방 상단에 고정한다 (내용은 원본이 지워져도 남도록 스냅샷)
ALTER TABLE chat_rooms ADD COLUMN notice_message_id BIGINT NULL;
ALTER TABLE chat_rooms ADD COLUMN notice_content VARCHAR(1000) NULL;
ALTER TABLE chat_rooms ADD COLUMN notice_by_name VARCHAR(100) NULL;
ALTER TABLE chat_rooms ADD COLUMN notice_at DATETIME NULL;
