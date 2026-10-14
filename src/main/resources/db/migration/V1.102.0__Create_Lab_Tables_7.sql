-- 케어브이 실험실 7차: 프로그램 연간계획·월간 계획표·의견수렴, 연간 사업계획서, 급여 만족도 응답, 입소 상담·대기.
-- 1~6차(V1.96.0~V1.101.0)와 같은 규칙 — lab_ 접두사 새 표만 더하고 기존 표는 건드리지 않는다.
-- 실험이 실패해도 이 6개 표를 DROP하면 본 서비스에는 흔적이 남지 않는다(서로 참조하지 않아 순서 무관).
--
-- 모든 표는 company_id를 company에 ON DELETE CASCADE로 묶는다. 만족도 응답·입소 상담의 elderly_id는
-- 비울 수 있고(익명 응답·아직 계약 전), elderly(node_id)에 ON DELETE SET NULL로 묶어
-- 어르신을 지워도 집계·상담 기록은 남긴다.
-- 의견 요약·반영, 만족도 의견, 입소 상담의 이름·연락처·욕구는 암호화(v2:)해 저장한다.
-- 암호문이 평문의 약 4.2배라 TEXT 또는 VARCHAR(512)로 둔다.

-- 1) 프로그램 연간계획: 기관 x 연도 x 분야(신체·인지·사회적응) 1건. 개인정보가 없어 암호화하지 않는다
CREATE TABLE lab_program_plans (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    company_id    BIGINT        NOT NULL,
    plan_year     SMALLINT      NOT NULL,
    category      VARCHAR(12)   NOT NULL COMMENT 'PHYSICAL | COGNITIVE | SOCIAL',
    goal          TEXT          NOT NULL COMMENT '목표, 500자 이하',
    content       TEXT          NOT NULL COMMENT '세부 프로그램·방법, 3000자 이하',
    frequency     VARCHAR(40)   NOT NULL COMMENT '예: 주 3회',
    target        VARCHAR(100)  NULL,
    budget        BIGINT        NULL COMMENT '0~1,000,000,000',
    written_on    DATE          NOT NULL,
    writer_name   VARCHAR(50)   NULL,
    ai_assisted   BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at    DATETIME(6)   NOT NULL,
    updated_at    DATETIME(6)   NOT NULL,

    PRIMARY KEY (id),
    UNIQUE KEY uk_lab_program_plans_company_year_category (company_id, plan_year, category),
    CONSTRAINT fk_lab_program_plans_company FOREIGN KEY (company_id)
        REFERENCES company (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2) 월간 프로그램 계획표: 기관 x 달 1건. items는 JSON(암호화 안 함)
CREATE TABLE lab_program_schedules (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    company_id       BIGINT        NOT NULL,
    schedule_month   CHAR(7)       NOT NULL COMMENT 'YYYY-MM',
    items            TEXT          NOT NULL COMMENT 'JSON [{date, startTime, category, title, instructor}] 200개 이하',
    notes            VARCHAR(500)  NULL,
    shared_on        DATE          NULL COMMENT '보호자에게 제공한 날 — shared_method와 함께 있거나 함께 없음',
    shared_method    VARCHAR(12)   NULL COMMENT 'PRINT | SMS | KAKAO | BOARD | OTHER',
    ai_assisted      BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at       DATETIME(6)   NOT NULL,
    updated_at       DATETIME(6)   NOT NULL,

    PRIMARY KEY (id),
    UNIQUE KEY uk_lab_program_schedules_company_month (company_id, schedule_month),
    CONSTRAINT fk_lab_program_schedules_company FOREIGN KEY (company_id)
        REFERENCES company (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3) 프로그램 의견수렴·반영: 반기마다 여러 건 가능
CREATE TABLE lab_program_feedback (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    company_id    BIGINT        NOT NULL,
    half          VARCHAR(7)    NOT NULL COMMENT 'YYYY-H1 | YYYY-H2',
    collected_on  DATE          NOT NULL COMMENT '그 반기 안',
    method        VARCHAR(12)   NOT NULL COMMENT 'SURVEY | INTERVIEW | MEETING | OTHER',
    participants  SMALLINT      NOT NULL COMMENT '0~500',
    summary       TEXT          NOT NULL COMMENT '의견 요약, 암호화(v2:), 평문 2000자 이하',
    reflection    TEXT          NULL COMMENT '반영 내용, 암호화, 평문 2000자 이하 — reflected_on과 함께 있거나 함께 없음',
    reflected_on  DATE          NULL COMMENT 'collected_on 이후',
    writer_name   VARCHAR(50)   NULL,
    ai_assisted   BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at    DATETIME(6)   NOT NULL,
    updated_at    DATETIME(6)   NOT NULL,

    PRIMARY KEY (id),
    KEY idx_lab_program_feedback_company_half (company_id, half),
    CONSTRAINT fk_lab_program_feedback_company FOREIGN KEY (company_id)
        REFERENCES company (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4) 연간 사업계획서: 기관 x 연도 1건. sections는 JSON(암호화 안 함)
CREATE TABLE lab_business_plans (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    company_id     BIGINT        NOT NULL,
    plan_year      SMALLINT      NOT NULL,
    sections       TEXT          NOT NULL COMMENT 'JSON {operation, service, staff}: 항목[{name, goal, content, target, budget, schedule}] 분야마다 20개 이하',
    total_income   BIGINT        NULL COMMENT '0~10,000,000,000',
    total_expense  BIGINT        NULL COMMENT '0~10,000,000,000',
    written_on     DATE          NULL,
    writer_name    VARCHAR(50)   NULL,
    ai_assisted    BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at     DATETIME(6)   NOT NULL,
    updated_at     DATETIME(6)   NOT NULL,

    PRIMARY KEY (id),
    UNIQUE KEY uk_lab_business_plans_company_year (company_id, plan_year),
    CONSTRAINT fk_lab_business_plans_company FOREIGN KEY (company_id)
        REFERENCES company (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5) 급여 만족도 응답: 어르신을 고르거나(같은 기관) 익명(elderly_id NULL)
CREATE TABLE lab_satisfaction_responses (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    company_id        BIGINT        NOT NULL,
    survey_date       DATE          NOT NULL COMMENT '오늘 이전',
    kind              VARCHAR(12)   NOT NULL COMMENT 'MEAL | PROGRAM | TRANSPORT | GENERAL',
    elderly_id        BIGINT        NULL COMMENT '익명 응답이면 NULL',
    respondent        VARCHAR(10)   NOT NULL COMMENT 'SELF | GUARDIAN',
    scores            TEXT          NOT NULL COMMENT 'JSON {문항코드: 1~5} 20개 이하, 코드는 ^[A-Z0-9_]{2,30}$',
    comment           TEXT          NULL COMMENT '의견, 암호화(v2:), 평문 1000자 이하',
    interviewer_name  VARCHAR(50)   NULL,
    created_at        DATETIME(6)   NOT NULL,
    updated_at        DATETIME(6)   NOT NULL,

    PRIMARY KEY (id),
    KEY idx_lab_satisfaction_company_kind_date (company_id, kind, survey_date),
    KEY idx_lab_satisfaction_elder (elderly_id),
    CONSTRAINT fk_lab_satisfaction_company FOREIGN KEY (company_id)
        REFERENCES company (id) ON DELETE CASCADE,
    CONSTRAINT fk_lab_satisfaction_elderly FOREIGN KEY (elderly_id)
        REFERENCES elderly (node_id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 6) 이용 문의·입소 상담·대기: 계약해 어르신으로 등록되면 elderly_id로 연결
CREATE TABLE lab_intake_inquiries (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    company_id      BIGINT        NOT NULL,
    inquiry_date    DATE          NOT NULL,
    applicant_name  VARCHAR(512)  NOT NULL COMMENT '신청 어르신 이름, 암호화(v2:), 평문 50자 이하',
    contact_name    VARCHAR(512)  NULL COMMENT '문의한 분, 암호화, 평문 50자 이하',
    contact_phone   VARCHAR(512)  NULL COMMENT '연락처, 암호화, 평문 20자 이하 — 있으면 consent_on 필수',
    relation        VARCHAR(20)   NULL COMMENT 'SELF | SPOUSE | CHILD | OTHER_FAMILY | OTHER',
    care_grade      VARCHAR(10)   NULL COMMENT '1 ~ 5 | cognitive | NONE | APPLYING',
    desired_days    VARCHAR(60)   NULL,
    needs           TEXT          NULL COMMENT '욕구, 암호화, 평문 2000자 이하',
    source          VARCHAR(12)   NOT NULL COMMENT 'PHONE | VISIT | REFERRAL | WEB | OTHER',
    status          VARCHAR(12)   NOT NULL COMMENT 'NEW | CONSULTED | WAITING | CONTRACTED | DECLINED | WITHDRAWN',
    decline_reason  VARCHAR(20)   NULL COMMENT 'CAPACITY | OUT_OF_AREA | SERVICE_MISMATCH | OTHER, DECLINED면 필수(노인장기요양보험법 제35조)',
    decline_note    VARCHAR(500)  NULL,
    consent_on      DATE          NULL COMMENT '개인정보 수집·이용 동의일',
    next_contact_on DATE          NULL,
    elderly_id      BIGINT        NULL COMMENT '계약해 등록된 어르신, CONTRACTED면 필수',
    counselor_name  VARCHAR(50)   NULL,
    ai_assisted     BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at      DATETIME(6)   NOT NULL,
    updated_at      DATETIME(6)   NOT NULL,

    PRIMARY KEY (id),
    KEY idx_lab_intake_company_status (company_id, status),
    KEY idx_lab_intake_elder (elderly_id),
    CONSTRAINT fk_lab_intake_company FOREIGN KEY (company_id)
        REFERENCES company (id) ON DELETE CASCADE,
    CONSTRAINT fk_lab_intake_elderly FOREIGN KEY (elderly_id)
        REFERENCES elderly (node_id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
