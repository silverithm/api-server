-- 13차: 종류별 기록 한 벌 — 업무수행일지(프로그램 관리자·선임 요양보호사), 시설운영일지, 수급자 안전관리 설명, 현금영수증 발행
CREATE TABLE lab_record_logs (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    company_id  BIGINT        NOT NULL,
    kind        VARCHAR(30)   NOT NULL COMMENT 'PROGRAM_MANAGER_LOG | SENIOR_CAREGIVER_LOG | FACILITY_DIARY | SAFETY_BRIEFING | CASH_RECEIPT',
    entry_date  DATE          NOT NULL COMMENT '오늘(서울) 이전',
    elderly_id  BIGINT        NULL COMMENT '수급자별 종류(SAFETY_BRIEFING·CASH_RECEIPT)만',
    author_name VARCHAR(50)   NOT NULL,
    checks      VARCHAR(300)  NOT NULL COMMENT '쉼표로 이은 확인 항목 코드(종류마다 허용 값이 다름)',
    body        TEXT          NULL COMMENT '암호화, 평문 2000자 이하',
    action      TEXT          NULL COMMENT '암호화, 평문 1000자 이하',
    extra       VARCHAR(512)  NULL COMMENT '암호화, 현금영수증 승인번호 평문 100자 이하',
    amount      BIGINT        NULL COMMENT '현금영수증 금액(원)',
    created_at  DATETIME(6)   NOT NULL,
    updated_at  DATETIME(6)   NOT NULL,

    PRIMARY KEY (id),
    KEY idx_lab_record_logs_company_kind_date (company_id, kind, entry_date),
    KEY idx_lab_record_logs_elder_kind_date (elderly_id, kind, entry_date),
    CONSTRAINT fk_lab_record_logs_company FOREIGN KEY (company_id)
        REFERENCES company (id) ON DELETE CASCADE,
    CONSTRAINT fk_lab_record_logs_elderly FOREIGN KEY (elderly_id)
        REFERENCES elderly (node_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
