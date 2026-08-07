-- 기관 전용 자료실 — 우리 기관 직원만 보는 내부 문서함.
-- 커뮤니티 자료실(plaza_library_items)은 전체 기관이 공유하지만, 이건 기관 안에서만 보인다.
CREATE TABLE company_library_items (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id   BIGINT       NOT NULL,
    category     VARCHAR(50)  NULL COMMENT '서식·매뉴얼·교육자료 등 기관이 정하는 분류',
    title        VARCHAR(200) NOT NULL,
    description  TEXT         NULL,
    file_name    VARCHAR(300) NOT NULL,
    file_size    BIGINT       NOT NULL DEFAULT 0,
    file_path    VARCHAR(500) NOT NULL,
    uploader_id  VARCHAR(100) NOT NULL,
    uploader_name VARCHAR(100) NOT NULL,
    created_at   DATETIME     NOT NULL,
    updated_at   DATETIME     NOT NULL,
    CONSTRAINT fk_company_library_company FOREIGN KEY (company_id) REFERENCES company (id) ON DELETE CASCADE,
    INDEX idx_company_library_company (company_id, created_at)
);
