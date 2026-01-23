-- 공지사항 테이블
CREATE TABLE notices (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(255) NOT NULL COMMENT '제목',
    content VARCHAR(5000) NOT NULL COMMENT '내용',
    priority ENUM('HIGH', 'NORMAL', 'LOW') NOT NULL DEFAULT 'NORMAL' COMMENT '우선순위',
    status ENUM('DRAFT', 'PUBLISHED', 'ARCHIVED') NOT NULL DEFAULT 'DRAFT' COMMENT '상태',
    is_pinned BOOLEAN NOT NULL DEFAULT FALSE COMMENT '상단 고정 여부',
    author_id VARCHAR(255) NOT NULL COMMENT '작성자 ID',
    author_name VARCHAR(255) NOT NULL COMMENT '작성자 이름',
    company_id BIGINT NOT NULL,
    view_count INT NOT NULL DEFAULT 0 COMMENT '조회수',
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    published_at DATETIME COMMENT '게시 일시',
    CONSTRAINT fk_notices_company FOREIGN KEY (company_id) REFERENCES company(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 인덱스 추가
CREATE INDEX idx_notices_company ON notices(company_id);
CREATE INDEX idx_notices_status ON notices(status);
CREATE INDEX idx_notices_is_pinned ON notices(is_pinned);
CREATE INDEX idx_notices_created_at ON notices(created_at);
