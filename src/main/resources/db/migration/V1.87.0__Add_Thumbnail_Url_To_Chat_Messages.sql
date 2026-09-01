-- V1.87.0: 채팅 이미지 메시지에 축소 썸네일 URL 추가
--
-- 채팅 목록에서 이미지 원본(수 MB)을 매번 내려받아 느렸다.
-- 업로드 시 긴 변 640px 축소본을 함께 만들어 S3에 올리고 그 URL을 저장한다.
-- 기존 행은 NULL로 두고 클라이언트가 fileUrl로 폴백하므로 백필하지 않는다.

ALTER TABLE chat_messages
    ADD COLUMN thumbnail_url VARCHAR(500) NULL;
