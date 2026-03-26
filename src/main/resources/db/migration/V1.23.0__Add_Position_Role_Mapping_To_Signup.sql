ALTER TABLE positions
    ADD COLUMN member_role VARCHAR(20) NULL COMMENT '기본 직원 역할(CAREGIVER/OFFICE)' AFTER description;

ALTER TABLE member_join_requests
    ADD COLUMN position_id BIGINT NULL COMMENT '가입 요청 선택 역할 FK' AFTER position;

ALTER TABLE member_join_requests
    ADD CONSTRAINT fk_member_join_requests_position
        FOREIGN KEY (position_id) REFERENCES positions(id) ON DELETE SET NULL;

ALTER TABLE member_join_requests
    ADD INDEX idx_member_join_requests_position_id (position_id);
