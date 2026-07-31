-- 일정 담당자 지정 (참석자와 구분되는 단일 담당자)
ALTER TABLE schedules
    ADD COLUMN manager_member_id BIGINT NULL,
    ADD COLUMN manager_name VARCHAR(255) NULL;
