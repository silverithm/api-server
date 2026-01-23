-- 공지사항 댓글 테이블
CREATE TABLE notice_comments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    notice_id BIGINT NOT NULL,
    author_id VARCHAR(255) NOT NULL COMMENT '작성자 ID',
    author_name VARCHAR(255) NOT NULL COMMENT '작성자 이름',
    content TEXT NOT NULL COMMENT '댓글 내용',
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    CONSTRAINT fk_notice_comments_notice FOREIGN KEY (notice_id) REFERENCES notices(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 공지사항 읽음 기록 테이블
CREATE TABLE notice_readers (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    notice_id BIGINT NOT NULL,
    user_id VARCHAR(255) NOT NULL COMMENT '사용자 ID',
    user_name VARCHAR(255) NOT NULL COMMENT '사용자 이름',
    read_at DATETIME NOT NULL COMMENT '읽은 시간',
    CONSTRAINT fk_notice_readers_notice FOREIGN KEY (notice_id) REFERENCES notices(id) ON DELETE CASCADE,
    CONSTRAINT uk_notice_readers UNIQUE (notice_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 일정 라벨 테이블
CREATE TABLE schedule_labels (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL COMMENT '라벨 이름',
    color VARCHAR(20) NOT NULL COMMENT '색상 코드 (hex)',
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    CONSTRAINT fk_schedule_labels_company FOREIGN KEY (company_id) REFERENCES company(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 일정 테이블
CREATE TABLE schedules (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL COMMENT '일정 제목',
    content TEXT COMMENT '일정 내용',
    category ENUM('MEETING', 'EVENT', 'TRAINING', 'OTHER') NOT NULL DEFAULT 'OTHER' COMMENT '카테고리',
    label_id BIGINT COMMENT '라벨 ID',
    location VARCHAR(255) COMMENT '장소',
    start_date DATE NOT NULL COMMENT '시작 날짜',
    start_time TIME COMMENT '시작 시간',
    end_date DATE NOT NULL COMMENT '종료 날짜',
    end_time TIME COMMENT '종료 시간',
    is_all_day BOOLEAN NOT NULL DEFAULT FALSE COMMENT '종일 여부',
    reminder ENUM('NONE', 'TEN_MIN', 'THIRTY_MIN', 'ONE_HOUR', 'ONE_DAY') DEFAULT 'NONE' COMMENT '알림 설정',
    author_id VARCHAR(255) NOT NULL COMMENT '작성자 ID',
    author_name VARCHAR(255) NOT NULL COMMENT '작성자 이름',
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    CONSTRAINT fk_schedules_company FOREIGN KEY (company_id) REFERENCES company(id),
    CONSTRAINT fk_schedules_label FOREIGN KEY (label_id) REFERENCES schedule_labels(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 인덱스 추가
CREATE INDEX idx_notice_comments_notice ON notice_comments(notice_id);
CREATE INDEX idx_notice_readers_notice ON notice_readers(notice_id);
CREATE INDEX idx_schedule_labels_company ON schedule_labels(company_id);
CREATE INDEX idx_schedules_company ON schedules(company_id);
CREATE INDEX idx_schedules_start_date ON schedules(start_date);
CREATE INDEX idx_schedules_end_date ON schedules(end_date);
CREATE INDEX idx_schedules_category ON schedules(category);
