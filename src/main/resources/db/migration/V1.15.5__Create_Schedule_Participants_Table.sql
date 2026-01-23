-- 일정 참가자 테이블
CREATE TABLE schedule_participants (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    schedule_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL COMMENT '참가자 멤버 ID',
    member_name VARCHAR(255) NOT NULL COMMENT '참가자 이름',
    status ENUM('PENDING', 'ACCEPTED', 'DECLINED') NOT NULL DEFAULT 'PENDING' COMMENT '참가 상태',
    responded_at DATETIME COMMENT '응답 시간',
    created_at DATETIME NOT NULL,
    CONSTRAINT fk_schedule_participants_schedule FOREIGN KEY (schedule_id) REFERENCES schedules(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 인덱스 추가
CREATE INDEX idx_schedule_participants_schedule ON schedule_participants(schedule_id);
CREATE INDEX idx_schedule_participants_member ON schedule_participants(member_id);
CREATE INDEX idx_schedule_participants_status ON schedule_participants(status);
