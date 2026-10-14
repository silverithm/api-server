-- 케어브이 실험실 4차: 직원 급여 기준, 월 급여대장, 후원금, 현금출납부.
-- 1~3차(V1.96.0~V1.98.0)와 같은 규칙 — lab_ 접두사 새 표만 더하고 기존 표는 건드리지 않는다.
-- 실험이 실패해도 이 4개 표를 DROP하면 본 서비스에는 흔적이 남지 않는다(lab_ledger_entries를 먼저 지운다).
--
-- 모든 표는 company_id를 company에 ON DELETE CASCADE로 묶는다.
-- member_id는 같은 기관 직원(members.id)이어야 하지만 FK는 걸지 않는다 — 직원이 퇴사로 지워져도
-- 이미 확정한 급여 기록(회계 증빙)은 남아야 하기 때문이다. 기관 소속 확인은 서비스가 한다.
-- 금액은 원 단위 정수다. 급여는 INT, 장부·후원금은 BIGINT(최대 100억).

-- 1) 직원별 급여 기준: 기관 x 직원 1건
CREATE TABLE lab_payroll_profiles (
    id                  BIGINT        NOT NULL AUTO_INCREMENT,
    company_id          BIGINT        NOT NULL,
    member_id           BIGINT        NOT NULL,
    job_type            VARCHAR(20)   NOT NULL COMMENT 'CAREGIVER | SOCIAL_WORKER | NURSE | NURSE_AIDE | THERAPIST | COOK | NUTRITIONIST | DRIVER | SANITATION | OFFICE | DIRECTOR | OTHER',
    pay_type            VARCHAR(10)   NOT NULL COMMENT 'MONTHLY | HOURLY',
    base_amount         INT           NOT NULL COMMENT '월급 또는 시급(1~20,000,000)',
    weekly_hours        DECIMAL(4,1)  NOT NULL COMMENT '주 소정근로시간(1~52)',
    dependents          SMALLINT      NOT NULL COMMENT '공제대상 가족 수, 본인 포함(1~11)',
    children            SMALLINT      NOT NULL COMMENT '8~20세 자녀 수(0~10)',
    withholding_rate    SMALLINT      NOT NULL COMMENT '원천징수 비율 80 | 100 | 120',
    meal_allowance      INT           NOT NULL DEFAULT 0 COMMENT '식대(0~200,000)',
    hire_date           DATE          NULL,
    senior_caregiver    BOOLEAN       NOT NULL DEFAULT FALSE,
    nps_enrolled        BOOLEAN       NOT NULL DEFAULT TRUE,
    health_enrolled     BOOLEAN       NOT NULL DEFAULT TRUE,
    employment_enrolled BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at          DATETIME(6)   NOT NULL,
    updated_at          DATETIME(6)   NOT NULL,

    PRIMARY KEY (id),
    -- 직원 1명에 기준 1건 — 동시 저장의 마지막 방어선
    UNIQUE KEY uk_lab_payroll_profiles_company_member (company_id, member_id),
    CONSTRAINT fk_lab_payroll_profiles_company FOREIGN KEY (company_id)
        REFERENCES company (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2) 월 급여대장 행: 기관 x 직원 x 월 1건. 합계는 서비스가 지급·공제 항목 합과 맞는지 검증한다
CREATE TABLE lab_payroll_lines (
    id                  BIGINT        NOT NULL AUTO_INCREMENT,
    company_id          BIGINT        NOT NULL,
    member_id           BIGINT        NOT NULL,
    pay_month           CHAR(7)       NOT NULL COMMENT 'YYYY-MM',
    regular_hours       DECIMAL(5,1)  NOT NULL DEFAULT 0,
    overtime_hours      DECIMAL(5,1)  NOT NULL DEFAULT 0,
    night_hours         DECIMAL(5,1)  NOT NULL DEFAULT 0,
    holiday_hours       DECIMAL(5,1)  NOT NULL DEFAULT 0,
    holiday_over8_hours DECIMAL(5,1)  NOT NULL DEFAULT 0,
    base_pay            INT           NOT NULL DEFAULT 0,
    overtime_pay        INT           NOT NULL DEFAULT 0,
    night_pay           INT           NOT NULL DEFAULT 0,
    holiday_pay         INT           NOT NULL DEFAULT 0,
    long_service_award  INT           NOT NULL DEFAULT 0,
    senior_allowance    INT           NOT NULL DEFAULT 0,
    other_allowances    TEXT          NULL COMMENT '기타 수당 JSON [{label, amount, taxable}] (10개 이하, label 30자 이하)',
    meal_allowance      INT           NOT NULL DEFAULT 0,
    gross_pay           INT           NOT NULL DEFAULT 0 COMMENT '지급 항목 합(식대 포함)',
    taxable_pay         INT           NOT NULL DEFAULT 0,
    nps                 INT           NOT NULL DEFAULT 0,
    health              INT           NOT NULL DEFAULT 0,
    ltc                 INT           NOT NULL DEFAULT 0,
    employment          INT           NOT NULL DEFAULT 0,
    income_tax          INT           NOT NULL DEFAULT 0,
    local_tax           INT           NOT NULL DEFAULT 0,
    other_deductions    TEXT          NULL COMMENT '기타 공제 JSON [{label, amount}] (10개 이하, label 30자 이하)',
    net_pay             INT           NOT NULL DEFAULT 0 COMMENT '지급 합 - 근로자 공제 합',
    employer_nps        INT           NOT NULL DEFAULT 0,
    employer_health     INT           NOT NULL DEFAULT 0,
    employer_ltc        INT           NOT NULL DEFAULT 0,
    employer_employment INT           NOT NULL DEFAULT 0,
    status              VARCHAR(10)   NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT | CONFIRMED',
    confirmed_at        DATETIME(6)   NULL,
    created_at          DATETIME(6)   NOT NULL,
    updated_at          DATETIME(6)   NOT NULL,

    PRIMARY KEY (id),
    UNIQUE KEY uk_lab_payroll_lines_company_member_month (company_id, member_id, pay_month),
    -- 월·연도 조회는 기관 + 월 범위로 읽는다
    KEY idx_lab_payroll_lines_company_month (company_id, pay_month),
    CONSTRAINT fk_lab_payroll_lines_company FOREIGN KEY (company_id)
        REFERENCES company (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3) 후원금: 후원자 이름은 개인정보라 암호화(평문 50자 이하 -> 암호문 512자 열)
CREATE TABLE lab_donations (
    id                 BIGINT        NOT NULL AUTO_INCREMENT,
    company_id         BIGINT        NOT NULL,
    received_on        DATE          NOT NULL,
    donor_name         VARCHAR(512)  NOT NULL COMMENT '암호화(v2:) 저장',
    donor_type         VARCHAR(20)   NOT NULL COMMENT 'INDIVIDUAL | CORPORATE | GROUP',
    kind               VARCHAR(10)   NOT NULL COMMENT 'CASH | GOODS',
    amount             BIGINT        NOT NULL COMMENT '원(1~10,000,000,000), 물품은 환산액',
    goods_description  VARCHAR(200)  NULL,
    designated_purpose VARCHAR(200)  NULL COMMENT '지정 후원 용도, 비지정이면 NULL',
    receipt_no         VARCHAR(30)   NULL,
    created_at         DATETIME(6)   NOT NULL,
    updated_at         DATETIME(6)   NOT NULL,

    PRIMARY KEY (id),
    -- 영수증 번호는 기관 안에서 유일 — NULL은 여러 건 허용
    UNIQUE KEY uk_lab_donations_company_receipt (company_id, receipt_no),
    KEY idx_lab_donations_company_date (company_id, received_on),
    CONSTRAINT fk_lab_donations_company FOREIGN KEY (company_id)
        REFERENCES company (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4) 현금출납부: 수입·지출 1건. 후원금이 지워지면 연결만 풀린다(ON DELETE SET NULL)
CREATE TABLE lab_ledger_entries (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    company_id    BIGINT        NOT NULL,
    entry_date    DATE          NOT NULL,
    direction     VARCHAR(10)   NOT NULL COMMENT 'INCOME | EXPENSE',
    account_code  CHAR(3)       NOT NULL COMMENT '계정 코드 3자리 숫자',
    description   VARCHAR(200)  NOT NULL,
    amount        BIGINT        NOT NULL COMMENT '원(1~10,000,000,000)',
    counterparty  VARCHAR(100)  NULL,
    evidence_type VARCHAR(20)   NULL COMMENT 'RECEIPT | CARD | TAX_INVOICE | TRANSFER | OTHER',
    donation_id   BIGINT        NULL COMMENT '연결된 후원금(같은 기관만, 서비스가 확인)',
    ai_assisted   BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at    DATETIME(6)   NOT NULL,
    updated_at    DATETIME(6)   NOT NULL,

    PRIMARY KEY (id),
    KEY idx_lab_ledger_entries_company_date (company_id, entry_date),
    KEY idx_lab_ledger_entries_donation (donation_id),
    CONSTRAINT fk_lab_ledger_entries_company FOREIGN KEY (company_id)
        REFERENCES company (id) ON DELETE CASCADE,
    CONSTRAINT fk_lab_ledger_entries_donation FOREIGN KEY (donation_id)
        REFERENCES lab_donations (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
