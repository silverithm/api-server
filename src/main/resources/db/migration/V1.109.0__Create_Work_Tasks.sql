-- 업무진행도 — 대시보드 오른쪽 패널에서 기관 전체가 함께 보는 업무 목록 (2026-10-22 원장 요청)
-- 등록할 때 담당자·기한·업무 내용·중요도·수행도를 적고, 다 끝날 때까지 담당자에게 매일 알림이 간다.
CREATE TABLE work_tasks (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    company_id      BIGINT        NOT NULL,
    content         VARCHAR(300)  NOT NULL COMMENT '업무 내용',
    importance      VARCHAR(10)   NOT NULL COMMENT 'HIGH | NORMAL | LOW',
    progress        INT           NOT NULL DEFAULT 0 COMMENT '수행도 0~100',
    due_date        DATE          NULL COMMENT '기한',
    created_by_id   VARCHAR(40)   NOT NULL COMMENT '등록한 사람 — 채팅 식별자(직원 ''12'', 관리자 ''admin_3'')',
    created_by_name VARCHAR(50)   NULL,
    completed_at    DATETIME(6)   NULL COMMENT '수행도 100이 된 시각',
    created_at      DATETIME(6)   NOT NULL,
    updated_at      DATETIME(6)   NOT NULL,

    PRIMARY KEY (id),
    KEY idx_work_tasks_company_completed (company_id, completed_at),
    CONSTRAINT fk_work_tasks_company FOREIGN KEY (company_id)
        REFERENCES company (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE work_task_assignees (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    work_task_id BIGINT       NOT NULL,
    person_id    VARCHAR(40)  NOT NULL COMMENT '담당자 — 채팅 식별자',
    person_name  VARCHAR(50)  NOT NULL,

    PRIMARY KEY (id),
    UNIQUE KEY uk_work_task_assignee (work_task_id, person_id),
    KEY idx_work_task_assignees_person (person_id),
    CONSTRAINT fk_work_task_assignees_task FOREIGN KEY (work_task_id)
        REFERENCES work_tasks (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 알림 종류에 WORK_TASK 추가. 운영 DB의 지금 목록(2026-10-22 확인) 끝에 하나만 더한다.
ALTER TABLE notifications
MODIFY COLUMN type ENUM(
    'VACATION_APPROVED',
    'VACATION_REJECTED',
    'VACATION_SUBMITTED',
    'VACATION_REMINDER',
    'MEMBER_JOIN_REQUESTED',
    'MEMBER_JOIN_APPROVED',
    'MEMBER_JOIN_REJECTED',
    'NOTICE',
    'CHAT',
    'APPROVAL',
    'MEETING_MINUTES',
    'GENERAL',
    'WORK_TASK'
) NOT NULL DEFAULT 'GENERAL' COMMENT '알림 타입';
