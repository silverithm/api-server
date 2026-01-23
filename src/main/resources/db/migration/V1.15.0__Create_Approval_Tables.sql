-- 전자결재 양식 테이블
CREATE TABLE approval_templates (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL COMMENT '양식 이름',
    description VARCHAR(500) COMMENT '양식 설명',
    file_url VARCHAR(255) NOT NULL COMMENT '양식 파일 URL',
    file_name VARCHAR(255) NOT NULL COMMENT '양식 파일명',
    file_size BIGINT NOT NULL COMMENT '파일 크기',
    is_active BOOLEAN NOT NULL DEFAULT TRUE COMMENT '활성화 여부',
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    CONSTRAINT fk_approval_templates_company FOREIGN KEY (company_id) REFERENCES company(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 전자결재 요청 테이블
CREATE TABLE approval_requests (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    template_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL COMMENT '결재 제목',
    requester_id VARCHAR(255) NOT NULL COMMENT '요청자 ID',
    requester_name VARCHAR(255) NOT NULL COMMENT '요청자 이름',
    status VARCHAR(20) NOT NULL COMMENT '상태 (PENDING, APPROVED, REJECTED)',
    attachment_url VARCHAR(255) COMMENT '첨부파일 URL',
    attachment_file_name VARCHAR(255) COMMENT '첨부파일명',
    attachment_file_size BIGINT COMMENT '첨부파일 크기',
    processed_by VARCHAR(255) COMMENT '처리자 ID',
    processed_by_name VARCHAR(255) COMMENT '처리자 이름',
    processed_at DATETIME COMMENT '처리 일시',
    reject_reason VARCHAR(1000) COMMENT '반려 사유',
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    CONSTRAINT fk_approval_requests_company FOREIGN KEY (company_id) REFERENCES company(id),
    CONSTRAINT fk_approval_requests_template FOREIGN KEY (template_id) REFERENCES approval_templates(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 인덱스 추가
CREATE INDEX idx_approval_templates_company ON approval_templates(company_id);
CREATE INDEX idx_approval_requests_company ON approval_requests(company_id);
CREATE INDEX idx_approval_requests_template ON approval_requests(template_id);
CREATE INDEX idx_approval_requests_status ON approval_requests(status);
CREATE INDEX idx_approval_requests_requester ON approval_requests(requester_id);
