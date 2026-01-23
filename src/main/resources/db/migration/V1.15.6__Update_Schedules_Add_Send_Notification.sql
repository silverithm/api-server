-- reminder 컬럼 제거 및 send_notification 컬럼 추가
ALTER TABLE schedules DROP COLUMN reminder;

ALTER TABLE schedules ADD COLUMN send_notification BOOLEAN NOT NULL DEFAULT FALSE COMMENT '알림 발송 여부';
