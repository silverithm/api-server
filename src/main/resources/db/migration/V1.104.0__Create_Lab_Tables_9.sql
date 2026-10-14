-- 케어브이 실험실 9차: 직원 인사 기준, 직원 이수·검진 기록, 통장, 통장 월말 잔액.
-- 1~8차(V1.96.0~V1.103.0)와 같은 규칙 — lab_ 접두사 새 표만 더하고 기존 표는 건드리지 않는다.
-- 실험이 실패해도 이 4개 표를 DROP하면 본 서비스에는 흔적이 남지 않는다(member_id는 표끼리·member와 FK 없이 서비스가 같은 기관인지 확인한다).
--
-- 모든 표는 company_id를 company에 ON DELETE CASCADE로 묶는다.
-- 통장 번호는 끝 4자리(account_tail)만 저장한다 — 전체 번호는 이 서비스가 갖지 않는다.

-- 1) 직원 인사 기준: 기관 x 직원 1건(경력직 비율·보수교육 대상·건강검진 주기를 계산하는 근거)
CREATE TABLE lab_staff_profiles (
    id                 BIGINT        NOT NULL AUTO_INCREMENT,
    company_id         BIGINT        NOT NULL,
    member_id          BIGINT        NOT NULL COMMENT '같은 기관 직원(서비스가 확인)',
    hire_date          DATE          NOT NULL COMMENT '기관 입사일',
    job_type           VARCHAR(20)   NOT NULL COMMENT 'CAREGIVER | SOCIAL_WORKER | NURSE | NURSE_AIDE | THERAPIST | COOK | NUTRITIONIST | DRIVER | SANITATION | OFFICE | DIRECTOR | OTHER',
    birth_year         SMALLINT      NULL COMMENT '1930~2010, 보수교육 홀수·짝수년생 대상',
    license_passed_on  DATE          NULL COMMENT '요양보호사 자격시험 합격일 - 2년 미만 보수교육 면제',
    office_worker      BOOLEAN       NOT NULL DEFAULT FALSE COMMENT '산업안전보건법상 사무직 - 건강검진 2년',
    note               VARCHAR(200)  NULL,
    created_at         DATETIME(6)   NOT NULL,
    updated_at         DATETIME(6)   NOT NULL,

    PRIMARY KEY (id),
    UNIQUE KEY uk_lab_staff_profiles_company_member (company_id, member_id),
    CONSTRAINT fk_lab_staff_profiles_company FOREIGN KEY (company_id)
        REFERENCES company (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2) 이수·검진 기록: 직원 1명의 1회
CREATE TABLE lab_staff_certs (
    id           BIGINT         NOT NULL AUTO_INCREMENT,
    company_id   BIGINT         NOT NULL,
    member_id    BIGINT         NOT NULL COMMENT '같은 기관 직원(서비스가 확인)',
    kind         VARCHAR(20)    NOT NULL COMMENT 'CONTINUING_EDU | HEALTH_CHECK | OTHER',
    done_on      DATE           NOT NULL COMMENT '오늘 이전(미래 날짜는 400)',
    hours        DECIMAL(4,1)   NULL COMMENT 'CONTINUING_EDU면 필수 0.5~40',
    institution  VARCHAR(100)   NULL,
    cert_no      VARCHAR(40)    NULL,
    note         VARCHAR(200)   NULL,
    created_at   DATETIME(6)    NOT NULL,
    updated_at   DATETIME(6)    NOT NULL,

    PRIMARY KEY (id),
    KEY idx_lab_staff_certs_member_kind (company_id, member_id, kind),
    CONSTRAINT fk_lab_staff_certs_company FOREIGN KEY (company_id)
        REFERENCES company (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3) 통장: 계좌번호는 끝 4자리만
CREATE TABLE lab_bank_accounts (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    company_id       BIGINT        NOT NULL,
    name             VARCHAR(50)   NOT NULL COMMENT '예: 운영 통장',
    purpose          VARCHAR(12)   NOT NULL COMMENT 'OPERATING | DONATION | DEPOSIT | OTHER',
    bank_name        VARCHAR(30)   NOT NULL,
    account_tail     CHAR(4)       NULL COMMENT '계좌번호 끝 4자리(숫자)만 - 전체 번호는 저장하지 않는다',
    opening_balance  BIGINT        NOT NULL COMMENT '-10,000,000,000 ~ 10,000,000,000',
    opening_date     DATE          NOT NULL,
    active           BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at       DATETIME(6)   NOT NULL,
    updated_at       DATETIME(6)   NOT NULL,

    PRIMARY KEY (id),
    KEY idx_lab_bank_accounts_company (company_id),
    CONSTRAINT fk_lab_bank_accounts_company FOREIGN KEY (company_id)
        REFERENCES company (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4) 통장 월말 잔액: 통장 x 달 1건
CREATE TABLE lab_bank_statements (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    company_id       BIGINT        NOT NULL,
    account_id       BIGINT        NOT NULL COMMENT '같은 기관 통장',
    statement_month  CHAR(7)       NOT NULL COMMENT 'YYYY-MM',
    closing_balance  BIGINT        NOT NULL COMMENT '-10,000,000,000 ~ 10,000,000,000',
    note             VARCHAR(200)  NULL,
    created_at       DATETIME(6)   NOT NULL,
    updated_at       DATETIME(6)   NOT NULL,

    PRIMARY KEY (id),
    UNIQUE KEY uk_lab_bank_statements_account_month (account_id, statement_month),
    KEY idx_lab_bank_statements_company_month (company_id, statement_month),
    CONSTRAINT fk_lab_bank_statements_company FOREIGN KEY (company_id)
        REFERENCES company (id) ON DELETE CASCADE,
    CONSTRAINT fk_lab_bank_statements_account FOREIGN KEY (account_id)
        REFERENCES lab_bank_accounts (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
