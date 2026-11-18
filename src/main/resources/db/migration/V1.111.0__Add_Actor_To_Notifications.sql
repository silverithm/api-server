-- 알림을 일으킨 사람(채팅 식별자: 직원 "12", 시설장 "admin_3") — 웹 알림 카드에 그 사람 사진을 붙인다 (2026-11-18)
-- 비어 있으면 시스템 알림(매일 아침 리마인더 등)이거나 이 칸이 생기기 전 알림이다.
ALTER TABLE notifications
    ADD COLUMN actor_id VARCHAR(64) NULL COMMENT '알림을 일으킨 사람의 채팅 식별자';
