-- 전자결재 공문화: 결재선(순차 다단계), 개인 서명, 기관 직인, 문서번호

-- 결재선 단계
CREATE TABLE approval_steps (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    approval_request_id BIGINT NOT NULL COMMENT '결재 요청 ID',
    step_order INT NOT NULL COMMENT '결재 순서 (1부터 시작)',
    approver_type VARCHAR(10) NOT NULL COMMENT '결재자 유형 (ADMIN=AppUser, MEMBER=Member)',
    approver_ref_id BIGINT NOT NULL COMMENT '결재자 PK (app_user.id 또는 members.id)',
    approver_id_legacy VARCHAR(255) NOT NULL COMMENT '프론트 호환용 결재자 문자열 (admin_<id> 또는 memberId)',
    approver_name VARCHAR(255) NOT NULL COMMENT '결재자 이름 (지정 시점 스냅샷)',
    role_label VARCHAR(20) NOT NULL COMMENT '역할 (REVIEWER=검토, FINAL=최종결재)',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '단계 상태 (PENDING, APPROVED, REJECTED, SKIPPED)',
    signature_url VARCHAR(1000) NULL COMMENT '서명 이미지 S3 상대경로',
    processed_at DATETIME NULL COMMENT '처리 일시',
    reject_reason VARCHAR(1000) NULL COMMENT '반려 사유 (해당 단계에서 반려 시)',
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    CONSTRAINT fk_approval_steps_request FOREIGN KEY (approval_request_id) REFERENCES approval_requests(id) ON DELETE CASCADE,
    CONSTRAINT uk_approval_steps_request_order UNIQUE (approval_request_id, step_order),
    INDEX idx_approval_steps_request (approval_request_id),
    INDEX idx_approval_steps_approver (approver_type, approver_ref_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='전자결재 결재선 단계';

-- 결재 요청: 문서번호/결재선 커서
ALTER TABLE approval_requests ADD COLUMN doc_number VARCHAR(50) NULL COMMENT '문서번호 (예: 2026-15)';
ALTER TABLE approval_requests ADD COLUMN doc_number_display VARCHAR(50) NULL COMMENT '문서번호 표시 형식 (예: 제 2026-15 호)';
ALTER TABLE approval_requests ADD COLUMN current_step_order INT NULL COMMENT '현재 대기중인 결재 단계 (결재선 없으면 NULL)';
ALTER TABLE approval_requests ADD COLUMN has_approval_line BOOLEAN NOT NULL DEFAULT FALSE COMMENT '결재선 사용 여부';
CREATE UNIQUE INDEX uk_approval_requests_doc_number ON approval_requests(company_id, doc_number);

-- 개인 서명 (직원/관리자)
ALTER TABLE members ADD COLUMN signature_url VARCHAR(1000) NULL COMMENT '등록된 결재 서명 이미지 S3 상대경로';
ALTER TABLE app_user ADD COLUMN signature_url VARCHAR(1000) NULL COMMENT '등록된 결재 서명 이미지 S3 상대경로';

-- 기관 직인
ALTER TABLE company ADD COLUMN seal_url VARCHAR(1000) NULL COMMENT '기관 직인 이미지 S3 상대경로';

-- 문서번호 채번 (회사·연도별, INSERT ... ON DUPLICATE KEY UPDATE 원자 upsert로 발급)
CREATE TABLE document_number_counters (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id BIGINT NOT NULL COMMENT '회사 ID',
    year INT NOT NULL COMMENT '연도',
    seq INT NOT NULL DEFAULT 0 COMMENT '해당 연도 마지막 발급 번호',
    CONSTRAINT uk_doc_number_counters_company_year UNIQUE (company_id, year),
    CONSTRAINT fk_doc_number_counters_company FOREIGN KEY (company_id) REFERENCES company(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='전자결재 문서번호 채번';
