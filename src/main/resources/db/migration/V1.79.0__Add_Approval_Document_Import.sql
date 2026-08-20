-- 다른 시스템(이카운트 ERP 등)에서 결재가 끝난 문서를 옮겨 담기 위한 자리.
--
-- 기관이 쓰던 그룹웨어를 케어브이로 바꿀 때, 이미 결재가 끝난 과거 문서를 통째로 버릴 수는 없다.
-- 다시 결재를 돌릴 수는 없으므로 "보관·열람용 기록"으로 들어온다 — 상태는 완료(승인/반려)로
-- 고정되고, 결재함에서 승인·반려 버튼이 나오지 않는다.
--
-- 문서번호는 우리 채번(doc_number, 회사+연도 유니크)과 섞으면 충돌하므로 원본 번호를 따로 둔다.
-- 기안자·결재자는 이름으로 계정을 찾아 붙이되, 퇴사해서 계정이 없으면 이름만 남긴다.

ALTER TABLE approval_requests
    ADD COLUMN is_imported BOOLEAN NOT NULL DEFAULT FALSE COMMENT '다른 시스템에서 옮겨온 완료 문서인지',
    ADD COLUMN imported_source VARCHAR(50) NULL COMMENT '가져온 곳 (예: ECOUNT)',
    ADD COLUMN external_doc_number VARCHAR(100) NULL COMMENT '원본 시스템의 문서번호 (표시·검색용)',
    ADD COLUMN imported_at DATETIME NULL COMMENT '이관 처리 일시';

-- 결재함이 이관 문서를 걸러 보거나 원본 번호로 찾을 때 쓴다
CREATE INDEX idx_approval_requests_imported ON approval_requests(company_id, is_imported);

-- 계정을 못 찾은 옛 결재자는 이름만 남긴다 (인가 비교는 (type, ref_id)라 NULL이면 아무와도 안 맞는다)
ALTER TABLE approval_steps
    MODIFY COLUMN approver_ref_id BIGINT NULL COMMENT '결재자 PK (app_user.id 또는 members.id). 이관 문서에서 계정을 못 찾으면 NULL',
    MODIFY COLUMN approver_id_legacy VARCHAR(255) NULL COMMENT '프론트 호환용 결재자 문자열. 이관 문서에서 계정을 못 찾으면 NULL';

-- 첨부가 하나뿐이라 원본 PDF와 딸린 첨부를 함께 담을 수 없었다.
-- 대표 파일은 지금처럼 approval_requests.attachment_url에 두고, 나머지를 여기에 쌓는다.
CREATE TABLE approval_request_attachments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    approval_request_id BIGINT NOT NULL COMMENT '결재 요청 ID',
    file_url VARCHAR(1000) NOT NULL COMMENT '저장 경로 (S3 상대경로)',
    file_name VARCHAR(255) NOT NULL COMMENT '원본 파일명',
    file_size BIGINT NULL COMMENT '파일 크기 (바이트)',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '표시 순서',
    created_at DATETIME NOT NULL,
    CONSTRAINT fk_approval_request_attachments_request FOREIGN KEY (approval_request_id) REFERENCES approval_requests(id) ON DELETE CASCADE,
    INDEX idx_approval_request_attachments_request (approval_request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='결재 문서의 추가 첨부파일';
