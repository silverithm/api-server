-- 케어브이 실험실 8차: 월 인력배치 점검, 수급자 서류함, 진료 동행 기록, 운영위원, 운영위원회 회의.
-- 1~7차(V1.96.0~V1.102.0)와 같은 규칙 — lab_ 접두사 새 표만 더하고 기존 표는 건드리지 않는다.
-- 실험이 실패해도 이 5개 표를 DROP하면 본 서비스에는 흔적이 남지 않는다(회의의 참석 위원은 JSON id라 표끼리 참조하지 않는다).
--
-- 모든 표는 company_id를 company에 ON DELETE CASCADE로 묶고, 어르신이 있는 표(서류함·진료 동행)는
-- elderly_id를 elderly(node_id)에 ON DELETE CASCADE로 묶는다.
-- 직원 목록(이름·근무시간)·진료 사유/내용/처방·보호자 메모·운영위원 이름·회의록은 암호화(v2:)해 저장한다.
-- 암호문이 평문의 약 4.2배라 TEXT 또는 VARCHAR(512)로 둔다. 서류 제목·위치는 암호화하지 않는다
-- (개인정보를 적지 않도록 화면이 안내한다).

-- 1) 월 인력배치 점검: 기관 x 달 1건
CREATE TABLE lab_staffing_months (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    company_id       BIGINT        NOT NULL,
    staffing_month   CHAR(7)       NOT NULL COMMENT 'YYYY-MM',
    service_days     SMALLINT      NOT NULL COMMENT '1~31',
    users_sum        INT           NOT NULL COMMENT '일자별 입소자 수 합, 0~100,000',
    extra_users_note VARCHAR(200)  NULL COMMENT '미이용일 급여비용 대상 등 더한 사유',
    holidays         TEXT          NOT NULL COMMENT 'JSON YYYY-MM-DD 배열, 그 달 날짜, 31개 이하',
    staff            TEXT          NOT NULL COMMENT '암호화 JSON [{memberId, name, jobType, hours}] 100명 이하',
    note             VARCHAR(500)  NULL,
    writer_name      VARCHAR(50)   NULL,
    created_at       DATETIME(6)   NOT NULL,
    updated_at       DATETIME(6)   NOT NULL,

    PRIMARY KEY (id),
    UNIQUE KEY uk_lab_staffing_months_company_month (company_id, staffing_month),
    CONSTRAINT fk_lab_staffing_months_company FOREIGN KEY (company_id)
        REFERENCES company (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2) 수급자 서류함: 서류 1건. 제목·위치는 평문이라 개인정보를 적지 않는다
CREATE TABLE lab_elder_documents (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    company_id    BIGINT        NOT NULL,
    elderly_id    BIGINT        NOT NULL,
    doc_type      VARCHAR(20)   NOT NULL COMMENT 'CONTRACT | RECOGNITION | USE_PLAN | NEEDS_ASSESSMENT | CARE_PLAN | PRIVACY_CONSENT | RIGHTS_GUIDE | RISK_ASSESSMENT | OTHER',
    title         VARCHAR(100)  NOT NULL,
    issued_on     DATE          NULL,
    expires_on    DATE          NULL COMMENT '발급일 이후',
    location      VARCHAR(100)  NULL COMMENT '보관 위치',
    checked_on    DATE          NULL,
    note          VARCHAR(500)  NULL,
    created_at    DATETIME(6)   NOT NULL,
    updated_at    DATETIME(6)   NOT NULL,

    PRIMARY KEY (id),
    KEY idx_lab_elder_documents_elder_type (elderly_id, doc_type),
    CONSTRAINT fk_lab_elder_documents_company FOREIGN KEY (company_id)
        REFERENCES company (id) ON DELETE CASCADE,
    CONSTRAINT fk_lab_elder_documents_elderly FOREIGN KEY (elderly_id)
        REFERENCES elderly (node_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3) 진료 동행 기록(평가 30 기준 1): 진료 1건
CREATE TABLE lab_medical_visits (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    company_id        BIGINT        NOT NULL,
    elderly_id        BIGINT        NOT NULL,
    visit_date        DATE          NOT NULL COMMENT '오늘(서울) 이전',
    start_time        CHAR(5)       NULL COMMENT 'HH:MM',
    end_time          CHAR(5)       NULL COMMENT 'HH:MM, 시작보다 뒤',
    hospital          VARCHAR(100)  NOT NULL,
    department        VARCHAR(40)   NULL,
    reason            TEXT          NULL COMMENT '암호화, 평문 1000자 이하',
    result            TEXT          NOT NULL COMMENT '암호화, 진료 내용 평문 2000자 이하',
    prescription      TEXT          NULL COMMENT '암호화, 평문 1000자 이하',
    escort_name       VARCHAR(50)   NOT NULL COMMENT '동행 직원',
    guardian_notified BOOLEAN       NOT NULL DEFAULT FALSE,
    guardian_note     VARCHAR(512)  NULL COMMENT '암호화, 평문 100자 이하',
    follow_up_on      DATE          NULL,
    follow_up_done    BOOLEAN       NOT NULL DEFAULT FALSE,
    ai_assisted       BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at        DATETIME(6)   NOT NULL,
    updated_at        DATETIME(6)   NOT NULL,

    PRIMARY KEY (id),
    KEY idx_lab_medical_visits_company_date (company_id, visit_date),
    KEY idx_lab_medical_visits_elder_date (elderly_id, visit_date),
    CONSTRAINT fk_lab_medical_visits_company FOREIGN KEY (company_id)
        REFERENCES company (id) ON DELETE CASCADE,
    CONSTRAINT fk_lab_medical_visits_elderly FOREIGN KEY (elderly_id)
        REFERENCES elderly (node_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4) 운영위원: 위원 1명 x 임기 1건
CREATE TABLE lab_committee_members (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    company_id    BIGINT        NOT NULL,
    name          VARCHAR(512)  NOT NULL COMMENT '암호화, 평문 50자 이하',
    role          VARCHAR(20)   NOT NULL COMMENT 'CHAIR | FACILITY_HEAD | STAFF | USER | GUARDIAN | EXPERT | LOCAL | PUBLIC_OFFICIAL | OTHER',
    term_start    DATE          NOT NULL,
    term_end      DATE          NOT NULL COMMENT '시작 이후 3년 이내',
    active        BOOLEAN       NOT NULL DEFAULT TRUE,
    note          VARCHAR(200)  NULL,
    created_at    DATETIME(6)   NOT NULL,
    updated_at    DATETIME(6)   NOT NULL,

    PRIMARY KEY (id),
    KEY idx_lab_committee_members_company_active (company_id, active),
    CONSTRAINT fk_lab_committee_members_company FOREIGN KEY (company_id)
        REFERENCES company (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5) 운영위원회 회의: 회의 1건. attendee_ids·decisions는 JSON(암호화 안 함), 회의록만 암호화
CREATE TABLE lab_committee_meetings (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    company_id    BIGINT        NOT NULL,
    held_on       DATE          NOT NULL,
    kind          VARCHAR(12)   NOT NULL COMMENT 'REGULAR | SPECIAL',
    attendee_ids  TEXT          NOT NULL COMMENT 'JSON 같은 기관 운영위원 id 배열, 30명 이하',
    agenda        TEXT          NOT NULL COMMENT '2000자 이하',
    minutes       TEXT          NULL COMMENT '암호화, 회의 내용 평문 5000자 이하',
    decisions     TEXT          NOT NULL COMMENT 'JSON 문자열 배열, 20개 이하·각 200자 이하',
    next_on       DATE          NULL,
    writer_name   VARCHAR(50)   NULL,
    ai_assisted   BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at    DATETIME(6)   NOT NULL,
    updated_at    DATETIME(6)   NOT NULL,

    PRIMARY KEY (id),
    KEY idx_lab_committee_meetings_company_held (company_id, held_on),
    CONSTRAINT fk_lab_committee_meetings_company FOREIGN KEY (company_id)
        REFERENCES company (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
