-- 결재 양식 버전 기록 (2026-11-16 숲속 업무일지 — 양식 항목을 복제·삭제하다 지난 문서 본문이 화면에서 사라졌다)
-- 양식을 저장할 때마다 그때 모습을 한 벌씩 남기고, 양식 관리에서 골라 보고 되돌린다(구글 시트 버전 기록처럼).
-- 열람 대상·사용 여부·순서는 버전에 넣지 않는다 — 문서 모양이 아니고, 따로 바로 바꾸는 값이다.
CREATE TABLE approval_template_versions (
    id                    BIGINT        NOT NULL AUTO_INCREMENT,
    template_id           BIGINT        NOT NULL,
    company_id            BIGINT        NOT NULL,
    version_no            INT           NOT NULL COMMENT '양식마다 1부터',
    kind                  VARCHAR(20)   NOT NULL COMMENT 'BASELINE(기록 시작 때 모습) | CREATED | EDITED | RESTORED',
    restored_from_no      INT           NULL COMMENT 'RESTORED일 때 되돌린 버전 번호',
    name                  VARCHAR(255)  NOT NULL,
    description           VARCHAR(500)  NULL,
    category              VARCHAR(50)   NULL,
    template_type         VARCHAR(10)   NOT NULL,
    form_schema           JSON          NULL,
    default_approval_line JSON          NULL,
    file_url              VARCHAR(255)  NULL,
    file_name             VARCHAR(255)  NULL,
    file_size             BIGINT        NULL,
    saved_by_id           VARCHAR(40)   NULL COMMENT '저장한 사람 — 채팅 식별자(직원 ''12'', 관리자 ''admin_3''). BASELINE은 없음',
    saved_by_name         VARCHAR(50)   NULL,
    created_at            DATETIME(6)   NOT NULL COMMENT '저장 시각(BASELINE은 그 양식의 마지막 수정 시각)',

    PRIMARY KEY (id),
    UNIQUE KEY uk_approval_template_version (template_id, version_no),
    KEY idx_approval_template_versions_company (company_id),
    CONSTRAINT fk_approval_template_versions_template FOREIGN KEY (template_id)
        REFERENCES approval_templates (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 지금 있는 양식은 지금 모습을 첫 버전으로 남긴다 — 배포 뒤 처음 고쳐도 되돌아갈 곳이 있다
INSERT INTO approval_template_versions
    (template_id, company_id, version_no, kind, name, description, category, template_type,
     form_schema, default_approval_line, file_url, file_name, file_size, created_at)
SELECT id, company_id, 1, 'BASELINE', name, description, category, COALESCE(template_type, 'file'),
       form_schema, default_approval_line, file_url, file_name, file_size, COALESCE(updated_at, created_at, NOW())
FROM approval_templates;
