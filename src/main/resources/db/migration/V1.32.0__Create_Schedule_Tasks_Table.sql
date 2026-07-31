-- 일정에 딸린 할 일(담당자별 업무) 테이블
-- 참석자(schedule_participants)와는 별개 개념:
--   참석자 = 그 일정에 참여하는 사람
--   할 일  = 그 일정에서 실제로 수행해야 하는 업무와 그 담당자
CREATE TABLE schedule_tasks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    schedule_id BIGINT NOT NULL,
    content VARCHAR(500) NOT NULL COMMENT '할 일 내용',
    assignee_member_id BIGINT NULL COMMENT '담당자 member id (미지정 가능)',
    assignee_name VARCHAR(255) NULL COMMENT '담당자 이름 스냅샷',
    is_completed BOOLEAN NOT NULL DEFAULT FALSE COMMENT '수행완료 여부',
    completed_at DATETIME NULL COMMENT '완료 처리 시각',
    completed_by_id VARCHAR(255) NULL COMMENT '완료 처리자 ID(email)',
    completed_by_name VARCHAR(255) NULL COMMENT '완료 처리자 이름',
    created_by_id VARCHAR(255) NULL COMMENT '등록자 ID(email)',
    created_by_name VARCHAR(255) NULL COMMENT '등록자 이름',
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    CONSTRAINT fk_schedule_tasks_schedule FOREIGN KEY (schedule_id) REFERENCES schedules(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_schedule_tasks_schedule ON schedule_tasks(schedule_id);
CREATE INDEX idx_schedule_tasks_assignee ON schedule_tasks(assignee_member_id, is_completed);
