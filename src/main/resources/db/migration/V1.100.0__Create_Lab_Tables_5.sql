-- 케어브이 실험실 5차: 상태변화·사고, 기능회복훈련, 보호자 월간 안내, 봉사자 활동, 외부기관 연계, 예산(본예산·추경).
-- 1~4차(V1.96.0~V1.99.0)와 같은 규칙 — lab_ 접두사 새 표만 더하고 기존 표는 건드리지 않는다.
-- 실험이 실패해도 이 7개 표를 DROP하면 본 서비스에는 흔적이 남지 않는다(lab_budget_lines를 먼저 지운다).
--
-- 모든 표는 company_id를 company에 ON DELETE CASCADE로 묶고, 어르신이 있는 표는 elderly_id를
-- elderly(node_id)에 ON DELETE CASCADE로 묶는다(자원 연계만 기관 전체 연계를 위해 NULL 허용).
-- 건강·자유서술·보호자·봉사자·담당자 이름은 암호화(v2:)해 저장한다. 암호문이 평문의 약 4.2배라 TEXT 또는 VARCHAR(512).

-- 1) 상태변화·사고·응급: 어르신 1명의 일어난 일 1건
CREATE TABLE lab_care_events (
    id                  BIGINT        NOT NULL AUTO_INCREMENT,
    company_id          BIGINT        NOT NULL,
    elderly_id          BIGINT        NOT NULL,
    occurred_at         DATETIME      NOT NULL COMMENT '서울 시각',
    kind                VARCHAR(20)   NOT NULL COMMENT 'CONDITION_CHANGE | FALL | INJURY | EMERGENCY | HOSPITAL | MEDICATION | BEHAVIOR | OTHER',
    severity            VARCHAR(10)   NOT NULL COMMENT 'LOW | MODERATE | HIGH | CRITICAL',
    place               VARCHAR(100)  NULL,
    description         TEXT          NOT NULL COMMENT '암호화(v2:) 저장, 평문 2000자 이하',
    immediate_action    TEXT          NULL COMMENT '암호화',
    guardian_notified_at DATETIME     NULL COMMENT '보호자 연락 시각(서울), guardian_method와 같이 있거나 같이 없음',
    guardian_method     VARCHAR(10)   NULL COMMENT 'PHONE | SMS | VISIT | OTHER',
    guardian_response   TEXT          NULL COMMENT '암호화',
    hospital_name       VARCHAR(100)  NULL,
    follow_up           TEXT          NULL COMMENT '암호화',
    prevention          TEXT          NULL COMMENT '암호화',
    status              VARCHAR(10)   NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN | CLOSED',
    closed_on           DATE          NULL,
    reporter_name       VARCHAR(50)   NULL,
    ai_assisted         BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at          DATETIME(6)   NOT NULL,
    updated_at          DATETIME(6)   NOT NULL,

    PRIMARY KEY (id),
    KEY idx_lab_care_events_company_time (company_id, occurred_at),
    KEY idx_lab_care_events_elder_time (elderly_id, occurred_at),
    CONSTRAINT fk_lab_care_events_company FOREIGN KEY (company_id)
        REFERENCES company (id) ON DELETE CASCADE,
    CONSTRAINT fk_lab_care_events_elderly FOREIGN KEY (elderly_id)
        REFERENCES elderly (node_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2) 기능회복훈련: 하루에 여러 번 허용(유일 키 없음)
CREATE TABLE lab_rehab_sessions (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    company_id      BIGINT        NOT NULL,
    elderly_id      BIGINT        NOT NULL,
    session_date    DATE          NOT NULL,
    kind            VARCHAR(20)   NOT NULL COMMENT 'PHYSICAL_FUNCTION | BASIC_MOTION | ADL | PHYSIO | OCCUPATIONAL | COGNITIVE',
    minutes         SMALLINT      NOT NULL COMMENT '1~240',
    items           TEXT          NULL COMMENT '훈련 항목 JSON 문자열 배열(10개 이하, 각 40자 이하), 암호화 안 함',
    content         TEXT          NULL COMMENT '암호화',
    reaction        TEXT          NULL COMMENT '암호화',
    therapist_name  VARCHAR(50)   NULL,
    ai_assisted     BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at      DATETIME(6)   NOT NULL,
    updated_at      DATETIME(6)   NOT NULL,

    PRIMARY KEY (id),
    KEY idx_lab_rehab_sessions_company_date (company_id, session_date),
    KEY idx_lab_rehab_sessions_elder_date (elderly_id, session_date),
    CONSTRAINT fk_lab_rehab_sessions_company FOREIGN KEY (company_id)
        REFERENCES company (id) ON DELETE CASCADE,
    CONSTRAINT fk_lab_rehab_sessions_elderly FOREIGN KEY (elderly_id)
        REFERENCES elderly (node_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3) 보호자 월간 안내: 어르신 x 월 1건
CREATE TABLE lab_guardian_notices (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    company_id    BIGINT        NOT NULL,
    elderly_id    BIGINT        NOT NULL,
    notice_month  CHAR(7)       NOT NULL COMMENT 'YYYY-MM',
    title         VARCHAR(100)  NOT NULL,
    body          TEXT          NOT NULL COMMENT '암호화, 평문 4000자 이하',
    facts         TEXT          NULL COMMENT '초안에 쓴 사실 묶음 JSON, 암호화(평문 8000자 이하)',
    status        VARCHAR(10)   NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT | SENT',
    sent_on       DATE          NULL,
    method        VARCHAR(10)   NULL COMMENT 'SMS | KAKAO | PRINT | EMAIL | VISIT | OTHER',
    recipient     VARCHAR(512)  NULL COMMENT '암호화(v2:) 저장, 평문 50자 이하',
    writer_name   VARCHAR(50)   NULL,
    ai_assisted   BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at    DATETIME(6)   NOT NULL,
    updated_at    DATETIME(6)   NOT NULL,

    PRIMARY KEY (id),
    -- 어르신 x 월 1건 — 동시 저장의 마지막 방어선
    UNIQUE KEY uk_lab_guardian_notices_elder_month (elderly_id, notice_month),
    KEY idx_lab_guardian_notices_company_month (company_id, notice_month),
    CONSTRAINT fk_lab_guardian_notices_company FOREIGN KEY (company_id)
        REFERENCES company (id) ON DELETE CASCADE,
    CONSTRAINT fk_lab_guardian_notices_elderly FOREIGN KEY (elderly_id)
        REFERENCES elderly (node_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4) 봉사자 활동: 봉사 시간(minutes)은 서버가 시작·끝 시각으로 계산한다
CREATE TABLE lab_volunteer_logs (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    company_id      BIGINT        NOT NULL,
    activity_date   DATE          NOT NULL,
    volunteer_name  VARCHAR(512)  NOT NULL COMMENT '암호화(v2:) 저장, 평문 50자 이하',
    organization    VARCHAR(100)  NULL,
    start_time      CHAR(5)       NOT NULL COMMENT 'HH:MM',
    end_time        CHAR(5)       NOT NULL COMMENT 'HH:MM',
    minutes         SMALLINT      NOT NULL COMMENT '1~720, 서버 계산',
    activity_type   VARCHAR(20)   NOT NULL COMMENT 'MEAL | PROGRAM | COMPANION | CLEANING | TALENT | OTHER',
    content         TEXT          NULL COMMENT '암호화',
    vms_registered  BOOLEAN       NOT NULL DEFAULT FALSE COMMENT '1365·VMS 실적 등록 여부',
    confirmer_name  VARCHAR(50)   NULL,
    created_at      DATETIME(6)   NOT NULL,
    updated_at      DATETIME(6)   NOT NULL,

    PRIMARY KEY (id),
    KEY idx_lab_volunteer_logs_company_date (company_id, activity_date),
    CONSTRAINT fk_lab_volunteer_logs_company FOREIGN KEY (company_id)
        REFERENCES company (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5) 외부기관·지역자원 연계: elderly_id가 NULL이면 기관 전체 연계
CREATE TABLE lab_resource_links (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    company_id      BIGINT        NOT NULL,
    elderly_id      BIGINT        NULL COMMENT '기관 전체 연계면 NULL',
    linked_on       DATE          NOT NULL,
    resource_type   VARCHAR(20)   NOT NULL COMMENT 'MEDICAL | HEALTH_CENTER | DEMENTIA_CENTER | WELFARE | COMMUNITY | GOODS | EDUCATION | OTHER',
    institution     VARCHAR(100)  NOT NULL,
    contact_person  VARCHAR(512)  NULL COMMENT '암호화(v2:) 저장, 평문 50자 이하',
    content         TEXT          NOT NULL COMMENT '암호화',
    outcome         TEXT          NULL COMMENT '암호화',
    follow_up_on    DATE          NULL,
    referral_given  BOOLEAN       NOT NULL DEFAULT FALSE COMMENT '연계기록지 제공 여부',
    writer_name     VARCHAR(50)   NULL,
    ai_assisted     BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at      DATETIME(6)   NOT NULL,
    updated_at      DATETIME(6)   NOT NULL,

    PRIMARY KEY (id),
    KEY idx_lab_resource_links_company_date (company_id, linked_on),
    KEY idx_lab_resource_links_elder (elderly_id),
    CONSTRAINT fk_lab_resource_links_company FOREIGN KEY (company_id)
        REFERENCES company (id) ON DELETE CASCADE,
    CONSTRAINT fk_lab_resource_links_elderly FOREIGN KEY (elderly_id)
        REFERENCES elderly (node_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 6) 예산: 기관 x 회계연도 x 차수(0=본예산, 1~9=추경)
CREATE TABLE lab_budgets (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    company_id    BIGINT        NOT NULL,
    fiscal_year   SMALLINT      NOT NULL COMMENT '2020~2100',
    revision      SMALLINT      NOT NULL COMMENT '0=본예산, 1~9=추경 차수',
    decided_on    DATE          NULL,
    status        VARCHAR(10)   NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT | CONFIRMED',
    note          VARCHAR(500)  NULL,
    confirmed_at  DATETIME(6)   NULL,
    created_at    DATETIME(6)   NOT NULL,
    updated_at    DATETIME(6)   NOT NULL,

    PRIMARY KEY (id),
    UNIQUE KEY uk_lab_budgets_company_year_revision (company_id, fiscal_year, revision),
    CONSTRAINT fk_lab_budgets_company FOREIGN KEY (company_id)
        REFERENCES company (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 7) 예산 줄: 예산 하나에 방향 x 계정 코드 1줄(≤300줄). 예산이 지워지면 줄도 지워진다
CREATE TABLE lab_budget_lines (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    budget_id     BIGINT        NOT NULL,
    company_id    BIGINT        NOT NULL,
    direction     VARCHAR(10)   NOT NULL COMMENT 'INCOME | EXPENSE',
    account_code  VARCHAR(4)    NOT NULL COMMENT '계정 코드 3~4자리 숫자',
    amount        BIGINT        NOT NULL COMMENT '원(0~10,000,000,000)',
    note          VARCHAR(200)  NULL,

    PRIMARY KEY (id),
    UNIQUE KEY uk_lab_budget_lines_budget_direction_code (budget_id, direction, account_code),
    CONSTRAINT fk_lab_budget_lines_budget FOREIGN KEY (budget_id)
        REFERENCES lab_budgets (id) ON DELETE CASCADE,
    CONSTRAINT fk_lab_budget_lines_company FOREIGN KEY (company_id)
        REFERENCES company (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
