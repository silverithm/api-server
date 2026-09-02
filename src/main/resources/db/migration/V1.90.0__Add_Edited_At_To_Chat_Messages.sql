-- V1.90.0: 채팅 메시지 수정 기능을 위한 수정 시각 컬럼 추가
--
-- 업무 기록 성격의 채팅이라 시간 제한 없이 수정을 허용하되, "수정됨" 표시와
-- 감사 추적을 위해 마지막으로 고친 시각을 남긴다. 기존 행은 한 번도 고친 적이
-- 없으므로 NULL로 두고 백필하지 않는다(NULL = 수정 이력 없음).

ALTER TABLE chat_messages
    ADD COLUMN edited_at DATETIME NULL;
