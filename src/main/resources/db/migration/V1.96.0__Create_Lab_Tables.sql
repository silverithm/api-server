-- 케어브이 실험실: 케어포 같은 상용 요양 SW가 하는 일을 하나씩 시험해 보는 표들.
-- 급여제공기록·제공대장, 본인부담금 청구·수납, 욕창(Braden)·낙상(Morse) 위험도 평가, 기관평가 준비 점검.
--
-- 기존 표를 건드리지 않고 lab_ 접두사로 새 표만 더한다. 실험이 실패해도 이 5개 표를 DROP하면
-- 본 서비스에는 흔적이 남지 않는다. 지금은 특정 기관(company.id 4)에만 열려 있고,
-- 접근 제한은 애플리케이션(LabGuardInterceptor)이 맡는다 — 표에는 기관 구분이 company_id로만 남는다.
--
-- 모든 표에 company_id를 두고 company에 ON DELETE CASCADE를 건다. 서비스가 매번 요청자의 기관으로
-- 걸러 읽지만, 기관이 지워질 때 건강 기록이 주인 없이 남는 것이 가장 나쁘기 때문이다.
-- 어르신 FK(elderly.node_id)도 같은 이유로 CASCADE다.
--
-- 건강·돌봄 문장과 평가 근거는 애플리케이션에서 AES-256-GCM('v2:' 접두사)으로 암호화해 넣는다.
-- 암호문이 평문의 약 4.2배라서 긴 글(평문 2000자 이하)은 TEXT, 짧은 글(평문 200자 이하)은
-- VARCHAR(1024)로 잡았다. 그래서 이 컬럼들은 DB에서 검색·정렬할 수 없다.
-- 점수(JSON)·금액·상태값은 개인을 알아볼 수 없고 집계에 쓰이므로 암호화하지 않는다.

-- 1) 급여제공기록: 어르신 1명 x 하루 1건
CREATE TABLE lab_care_records (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    company_id    BIGINT       NOT NULL,
    elderly_id    BIGINT       NOT NULL,
    record_date   DATE         NOT NULL,

    physical      TEXT         NULL COMMENT '신체활동지원(암호화)',
    cognitive     TEXT         NULL COMMENT '인지활동 및 일상생활 함께하기(암호화)',
    nursing       TEXT         NULL COMMENT '건강·간호 관리(암호화)',
    training      TEXT         NULL COMMENT '기능회복훈련(암호화)',
    special_note  TEXT         NULL COMMENT '특이사항(암호화)',
    raw_note      TEXT         NULL COMMENT 'AI에 넣기 전 원문 메모(암호화)',
    meal          VARCHAR(1024) NULL COMMENT '식사(암호화, 평문 200자 이하)',
    toilet        VARCHAR(1024) NULL COMMENT '배설(암호화, 평문 200자 이하)',
    bath          VARCHAR(1024) NULL COMMENT '목욕(암호화, 평문 200자 이하)',

    provider_name VARCHAR(50)  NULL COMMENT '제공자(요양보호사) 이름',
    ai_assisted   BOOLEAN      NOT NULL DEFAULT FALSE COMMENT 'AI가 구조화를 도왔는지',

    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,

    PRIMARY KEY (id),
    -- 같은 날 같은 어르신 기록이 둘이 되면 안 된다 — 동시 저장의 마지막 방어선
    UNIQUE KEY uk_lab_care_records_elder_date (elderly_id, record_date),
    KEY idx_lab_care_records_company_date (company_id, record_date),
    CONSTRAINT fk_lab_care_records_company FOREIGN KEY (company_id)
        REFERENCES company (id) ON DELETE CASCADE,
    CONSTRAINT fk_lab_care_records_elderly FOREIGN KEY (elderly_id)
        REFERENCES elderly (node_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2) 제공대장: 월별 급여제공기록지를 수급자·보호자에게 전달한 내역(어르신 1명 x 월 1건)
CREATE TABLE lab_care_deliveries (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    company_id    BIGINT       NOT NULL,
    elderly_id    BIGINT       NOT NULL,
    record_month  CHAR(7)      NOT NULL COMMENT 'YYYY-MM',
    delivered_on  DATE         NOT NULL,
    method        VARCHAR(20)  NOT NULL COMMENT 'VISIT | MAIL | FAX | ELECTRONIC',
    recipient     VARCHAR(50)  NULL COMMENT '받은 사람',
    note          VARCHAR(1024) NULL COMMENT '암호화, 평문 200자 이하',
    created_at    DATETIME(6)  NOT NULL,

    PRIMARY KEY (id),
    UNIQUE KEY uk_lab_care_deliveries_elder_month (elderly_id, record_month),
    KEY idx_lab_care_deliveries_company_month (company_id, record_month),
    CONSTRAINT fk_lab_care_deliveries_company FOREIGN KEY (company_id)
        REFERENCES company (id) ON DELETE CASCADE,
    CONSTRAINT fk_lab_care_deliveries_elderly FOREIGN KEY (elderly_id)
        REFERENCES elderly (node_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3) 본인부담금 청구·수납: 어르신 1명 x 월 1건. 금액은 모두 원 단위 정수
-- total_due와 non_covered_amount, status는 서버가 다시 계산해 넣는다(클라이언트 값을 믿지 않는다).
CREATE TABLE lab_copay_bills (
    id                  BIGINT      NOT NULL AUTO_INCREMENT,
    company_id          BIGINT      NOT NULL,
    elderly_id          BIGINT      NOT NULL,
    bill_month          CHAR(7)     NOT NULL COMMENT 'YYYY-MM',

    care_grade          VARCHAR(20) NOT NULL COMMENT '1~5 | cognitive',
    copay_class         VARCHAR(20) NOT NULL COMMENT 'standard | reduced40 | reduced60 | medicalAid',
    daycare_unit        VARCHAR(20) NOT NULL COMMENT 'general | dementia',
    hour_band           VARCHAR(20) NOT NULL COMMENT 'h3to6 | h6to8 | h8to10 | h10to13 | over13',
    days                INT         NOT NULL COMMENT '이용 일수 0~31',

    daily_fee           INT         NOT NULL DEFAULT 0 COMMENT '1일 급여비용',
    covered_total       INT         NOT NULL DEFAULT 0 COMMENT '급여비용 총액',
    copay_amount        INT         NOT NULL DEFAULT 0 COMMENT '한도 안 본인부담금',
    over_limit_amount   INT         NOT NULL DEFAULT 0 COMMENT '한도 초과 전액 본인부담',
    non_covered_items   TEXT        NULL COMMENT '비급여 항목 JSON [{"label":..,"amount":..}]',
    non_covered_amount  INT         NOT NULL DEFAULT 0 COMMENT '비급여 합계(서버 계산)',
    previous_unpaid     INT         NOT NULL DEFAULT 0 COMMENT '전월 미수금',
    total_due           INT         NOT NULL DEFAULT 0 COMMENT '이번 달 청구액(서버 계산)',

    paid_amount         INT         NOT NULL DEFAULT 0 COMMENT '지금까지 낸 금액',
    last_paid_on        DATE        NULL,
    payer_name          VARCHAR(50) NULL,
    status              VARCHAR(20) NOT NULL COMMENT 'UNPAID | PARTIAL | PAID',

    created_at          DATETIME(6) NOT NULL,
    updated_at          DATETIME(6) NOT NULL,

    PRIMARY KEY (id),
    UNIQUE KEY uk_lab_copay_bills_elder_month (elderly_id, bill_month),
    KEY idx_lab_copay_bills_company_month (company_id, bill_month),
    CONSTRAINT fk_lab_copay_bills_company FOREIGN KEY (company_id)
        REFERENCES company (id) ON DELETE CASCADE,
    CONSTRAINT fk_lab_copay_bills_elderly FOREIGN KEY (elderly_id)
        REFERENCES elderly (node_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4) 위험도 평가: 욕창(BRADEN)·낙상(MORSE). 평가는 덮어쓰지 않고 쌓아 이력을 남긴다(반기 점검 근거)
CREATE TABLE lab_risk_assessments (
    id             BIGINT      NOT NULL AUTO_INCREMENT,
    company_id     BIGINT      NOT NULL,
    elderly_id     BIGINT      NOT NULL,
    kind           VARCHAR(10) NOT NULL COMMENT 'BRADEN | MORSE',
    scores         TEXT        NOT NULL COMMENT '항목별 점수 JSON {"sensory":3,...}',
    total          INT         NOT NULL COMMENT '서버 계산 합계',
    risk_level     VARCHAR(20) NOT NULL COMMENT '서버 계산 단계',
    assessed_on    DATE        NOT NULL,
    assessor_name  VARCHAR(50) NULL,
    note           TEXT        NULL COMMENT '암호화',
    ai_assisted    BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at     DATETIME(6) NOT NULL,

    PRIMARY KEY (id),
    KEY idx_lab_risk_assessments_elder_kind_date (elderly_id, kind, assessed_on),
    KEY idx_lab_risk_assessments_company (company_id),
    CONSTRAINT fk_lab_risk_assessments_company FOREIGN KEY (company_id)
        REFERENCES company (id) ON DELETE CASCADE,
    CONSTRAINT fk_lab_risk_assessments_elderly FOREIGN KEY (elderly_id)
        REFERENCES elderly (node_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5) 기관평가 준비 점검: 기관 x 지표 1건. 어르신과 무관해 elderly FK가 없다
CREATE TABLE lab_eval_checks (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    company_id      BIGINT       NOT NULL,
    indicator_code  VARCHAR(40)  NOT NULL,
    status          VARCHAR(20)  NOT NULL COMMENT 'READY | PARTIAL | MISSING | NOT_APPLICABLE',
    evidence        TEXT         NULL COMMENT '근거 메모(암호화)',
    ai_comment      TEXT         NULL COMMENT 'AI 검토 의견(암호화)',
    updated_by      VARCHAR(50)  NULL,
    updated_at      DATETIME(6)  NOT NULL,

    PRIMARY KEY (id),
    UNIQUE KEY uk_lab_eval_checks_company_code (company_id, indicator_code),
    CONSTRAINT fk_lab_eval_checks_company FOREIGN KEY (company_id)
        REFERENCES company (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
