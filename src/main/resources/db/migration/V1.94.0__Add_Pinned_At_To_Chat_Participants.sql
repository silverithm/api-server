-- 자주 쓰는 채팅방을 목록 맨 위에 고정한다 (2026-09-24 버그제보방 요청).
-- 고정은 사람마다 다르므로 방이 아니라 참가 정보에 둔다. 비어 있으면 고정 안 함.
ALTER TABLE chat_participants ADD COLUMN pinned_at DATETIME NULL;
