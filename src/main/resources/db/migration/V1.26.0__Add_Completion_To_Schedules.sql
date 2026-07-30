-- 월간일정 수행완료(진행도) 추적 컬럼 추가
ALTER TABLE schedules ADD COLUMN is_completed BOOLEAN NOT NULL DEFAULT FALSE COMMENT '수행완료 여부';
ALTER TABLE schedules ADD COLUMN completed_at DATETIME NULL COMMENT '수행완료 처리 시각';
ALTER TABLE schedules ADD COLUMN completed_by_id VARCHAR(255) NULL COMMENT '수행완료 처리자 ID(email)';
ALTER TABLE schedules ADD COLUMN completed_by_name VARCHAR(255) NULL COMMENT '수행완료 처리자 이름';

CREATE INDEX idx_schedules_is_completed ON schedules(is_completed);
