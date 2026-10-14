-- 케어브이 실험실 6차: 투약의뢰서·투약 기록, 약품 관리대장·보관 점검, 목욕 제공 일지, 급여 계약·장기요양 인정,
-- 퇴소 연계기록지, 공단 청구 대조.
-- 1~5차(V1.96.0~V1.100.0)와 같은 규칙 — lab_ 접두사 새 표만 더하고 기존 표는 건드리지 않는다.
-- 실험이 실패해도 이 8개 표를 DROP하면 본 서비스에는 흔적이 남지 않는다
-- (lab_medication_doses → lab_medication_requests, lab_discharge_referrals → lab_elder_contracts 순으로 지운다).
--
-- 모든 표는 company_id를 company에 ON DELETE CASCADE로 묶고, 어르신이 있는 표는 elderly_id를
-- elderly(node_id)에 ON DELETE CASCADE로 묶는다. 건강·의뢰자·인정번호·연계기록지 글·수령자는 암호화(v2:)해 저장한다.
-- 암호문이 평문의 약 4.2배라 TEXT 또는 VARCHAR(512)로 둔다.

-- 1) 투약의뢰서: 보호자가 맡긴 약. 약 목록(JSON)은 건강 정보라 통째로 암호화한다
CREATE TABLE lab_medication_requests (
    id                    BIGINT        NOT NULL AUTO_INCREMENT,
    company_id            BIGINT        NOT NULL,
    elderly_id            BIGINT        NOT NULL,
    requested_on          DATE          NOT NULL,
    requester_name        VARCHAR(512)  NOT NULL COMMENT '암호화(v2:) 저장, 평문 50자 이하',
    requester_relation    VARCHAR(20)   NOT NULL COMMENT 'SELF | SPOUSE | CHILD | OTHER_FAMILY | OTHER',
    medicines             TEXT          NOT NULL COMMENT '암호화, JSON [{name, dose, route, slots, timing, note}] 1~15개',
    start_date            DATE          NOT NULL,
    end_date              DATE          NULL COMMENT 'start_date 이후',
    prescription_checked  BOOLEAN       NOT NULL DEFAULT FALSE COMMENT '의사 처방 확인',
    status                VARCHAR(10)   NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE | ENDED',
    note                  TEXT          NULL COMMENT '암호화, 평문 500자 이하',
    writer_name           VARCHAR(50)   NULL,
    ai_assisted           BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at            DATETIME(6)   NOT NULL,
    updated_at            DATETIME(6)   NOT NULL,

    PRIMARY KEY (id),
    KEY idx_lab_medication_requests_company_status (company_id, status),
    KEY idx_lab_medication_requests_elder_start (elderly_id, start_date),
    CONSTRAINT fk_lab_medication_requests_company FOREIGN KEY (company_id)
        REFERENCES company (id) ON DELETE CASCADE,
    CONSTRAINT fk_lab_medication_requests_elderly FOREIGN KEY (elderly_id)
        REFERENCES elderly (node_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2) 투약 기록: 의뢰서 x 날짜 x 시간대 1건
CREATE TABLE lab_medication_doses (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    company_id    BIGINT        NOT NULL,
    elderly_id    BIGINT        NOT NULL,
    request_id    BIGINT        NOT NULL,
    given_on      DATE          NOT NULL,
    slot          VARCHAR(10)   NOT NULL COMMENT 'MORNING | LUNCH | EVENING | BEDTIME',
    status        VARCHAR(10)   NOT NULL COMMENT 'GIVEN | SKIPPED | REFUSED',
    given_at      CHAR(5)       NULL COMMENT 'HH:MM, GIVEN이면 필수',
    giver_name    VARCHAR(50)   NOT NULL,
    observation   TEXT          NULL COMMENT '투약 후 상태, 암호화, 평문 500자 이하',
    created_at    DATETIME(6)   NOT NULL,
    updated_at    DATETIME(6)   NOT NULL,

    PRIMARY KEY (id),
    -- 같은 의뢰서의 같은 날 같은 시간대는 한 건 — 동시 저장의 마지막 방어선
    UNIQUE KEY uk_lab_medication_doses_request_date_slot (request_id, given_on, slot),
    KEY idx_lab_medication_doses_company_date (company_id, given_on),
    KEY idx_lab_medication_doses_elder_date (elderly_id, given_on),
    CONSTRAINT fk_lab_medication_doses_company FOREIGN KEY (company_id)
        REFERENCES company (id) ON DELETE CASCADE,
    CONSTRAINT fk_lab_medication_doses_elderly FOREIGN KEY (elderly_id)
        REFERENCES elderly (node_id) ON DELETE CASCADE,
    CONSTRAINT fk_lab_medication_doses_request FOREIGN KEY (request_id)
        REFERENCES lab_medication_requests (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3) 약품 관리대장(일반·비상 의약품): 개인정보가 없어 암호화하지 않는다
CREATE TABLE lab_medicine_stock (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    company_id   BIGINT        NOT NULL,
    name         VARCHAR(60)   NOT NULL,
    efficacy     VARCHAR(100)  NOT NULL COMMENT '효능',
    quantity     INT           NOT NULL COMMENT '0~100000',
    unit         VARCHAR(10)   NOT NULL,
    storage      VARCHAR(10)   NOT NULL COMMENT 'ROOM | FRIDGE',
    expires_on   DATE          NOT NULL,
    opened_on    DATE          NULL,
    disposed_on  DATE          NULL COMMENT '폐기한 날 — 약품 점검에서 DISPOSE로 표시하면 서버가 채운다',
    note         VARCHAR(200)  NULL,
    created_at   DATETIME(6)   NOT NULL,
    updated_at   DATETIME(6)   NOT NULL,

    PRIMARY KEY (id),
    KEY idx_lab_medicine_stock_company_expires (company_id, expires_on),
    CONSTRAINT fk_lab_medicine_stock_company FOREIGN KEY (company_id)
        REFERENCES company (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4) 약품 보관 점검: 기관 x 점검일 1건. items는 약품별 확인·조치 JSON(암호화 안 함)
CREATE TABLE lab_medicine_checks (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    company_id      BIGINT        NOT NULL,
    checked_on      DATE          NOT NULL,
    inspector_name  VARCHAR(50)   NOT NULL,
    cabinet_locked  BOOLEAN       NOT NULL COMMENT '보관함 잠금',
    fridge_locked   BOOLEAN       NULL COMMENT '냉장고 잠금 — 냉장 보관 약이 없으면 NULL',
    items           TEXT          NOT NULL COMMENT 'JSON [{stockId, ok, action, note}] 200개 이하',
    note            VARCHAR(500)  NULL,
    created_at      DATETIME(6)   NOT NULL,
    updated_at      DATETIME(6)   NOT NULL,

    PRIMARY KEY (id),
    UNIQUE KEY uk_lab_medicine_checks_company_date (company_id, checked_on),
    CONSTRAINT fk_lab_medicine_checks_company FOREIGN KEY (company_id)
        REFERENCES company (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5) 목욕 제공 일지: 분(minutes)은 서버가 시작·끝 시각으로 계산한다
CREATE TABLE lab_bath_logs (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    company_id     BIGINT        NOT NULL,
    elderly_id     BIGINT        NOT NULL,
    bath_date      DATE          NOT NULL,
    start_time     CHAR(5)       NOT NULL COMMENT 'HH:MM',
    end_time       CHAR(5)       NOT NULL COMMENT 'HH:MM',
    minutes        SMALLINT      NOT NULL COMMENT '1~180, 서버 계산',
    method         VARCHAR(12)   NOT NULL COMMENT 'FULL_BATH | BATH_CHAIR | BED',
    provider_name  VARCHAR(50)   NOT NULL COMMENT '몸을 씻긴 요양보호사',
    pre_check      VARCHAR(512)  NULL COMMENT '목욕 전 상태, 암호화(v2:) 저장, 평문 100자 이하',
    skin_note      TEXT          NULL COMMENT '피부·상처 관찰, 암호화, 평문 500자 이하',
    note           TEXT          NULL COMMENT '암호화, 평문 500자 이하',
    created_at     DATETIME(6)   NOT NULL,
    updated_at     DATETIME(6)   NOT NULL,

    PRIMARY KEY (id),
    KEY idx_lab_bath_logs_company_date (company_id, bath_date),
    KEY idx_lab_bath_logs_elder_date (elderly_id, bath_date),
    CONSTRAINT fk_lab_bath_logs_company FOREIGN KEY (company_id)
        REFERENCES company (id) ON DELETE CASCADE,
    CONSTRAINT fk_lab_bath_logs_elderly FOREIGN KEY (elderly_id)
        REFERENCES elderly (node_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 6) 급여 계약·장기요양 인정: 어르신 1명의 진행 중(ACTIVE) 계약은 하나만
CREATE TABLE lab_elder_contracts (
    id                    BIGINT        NOT NULL AUTO_INCREMENT,
    company_id            BIGINT        NOT NULL,
    elderly_id            BIGINT        NOT NULL,
    contract_start        DATE          NOT NULL,
    contract_end          DATE          NULL,
    care_grade            VARCHAR(10)   NOT NULL COMMENT '1 ~ 5 | cognitive',
    recognition_no        VARCHAR(512)  NULL COMMENT '장기요양인정번호, 암호화(v2:) 저장, 평문 20자 이하',
    valid_from            DATE          NOT NULL,
    valid_to              DATE          NOT NULL COMMENT 'valid_from 이후',
    renewal_count         SMALLINT      NOT NULL DEFAULT 0 COMMENT '0=최초 인정, 1 이상=갱신',
    copay_class           VARCHAR(20)   NOT NULL COMMENT 'standard | reduced40 | reduced60 | medicalAid',
    planned_days_per_month SMALLINT     NULL COMMENT '1~31, 15 이상이면 미이용일 급여비용 대상',
    signed_on             DATE          NULL,
    copy_given            BOOLEAN       NOT NULL DEFAULT FALSE COMMENT '2부 중 1부 수급자 교부',
    contract_notified_on  DATE          NULL COMMENT '공단 계약통보',
    plan_notified_on      DATE          NULL COMMENT '급여제공계획 통보 — 급여제공 시작일까지',
    rights_guided_on      DATE          NULL COMMENT '노인인권 지침 안내 — 시작일부터 14일 안',
    status                VARCHAR(10)   NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE | ENDED',
    end_reason            VARCHAR(20)   NULL COMMENT 'TRANSFER | HOSPITAL | DEATH | SELF | OTHER, ENDED면 필수',
    note                  TEXT          NULL COMMENT '암호화, 평문 1000자 이하',
    created_at            DATETIME(6)   NOT NULL,
    updated_at            DATETIME(6)   NOT NULL,
    -- MySQL에는 부분 유니크 키가 없어 ACTIVE일 때만 어르신 id가 값이 되는 가상 열에 유니크를 건다.
    -- ENDED 행은 NULL이라 여러 건이 있어도 되고, ACTIVE 둘을 동시에 저장하면 한쪽이 막힌다(서비스가 409로 바꾼다).
    active_elderly_id     BIGINT        GENERATED ALWAYS AS (CASE WHEN status = 'ACTIVE' THEN elderly_id ELSE NULL END) VIRTUAL,

    PRIMARY KEY (id),
    UNIQUE KEY uk_lab_elder_contracts_active_elder (active_elderly_id),
    KEY idx_lab_elder_contracts_company_status (company_id, status),
    KEY idx_lab_elder_contracts_elder_start (elderly_id, contract_start),
    CONSTRAINT fk_lab_elder_contracts_company FOREIGN KEY (company_id)
        REFERENCES company (id) ON DELETE CASCADE,
    CONSTRAINT fk_lab_elder_contracts_elderly FOREIGN KEY (elderly_id)
        REFERENCES elderly (node_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 7) 퇴소 연계기록지: 어르신 x 퇴소일 1건. 제공대장 네 칸은 함께 있거나 함께 없어야 한다
CREATE TABLE lab_discharge_referrals (
    id                  BIGINT        NOT NULL AUTO_INCREMENT,
    company_id          BIGINT        NOT NULL,
    elderly_id          BIGINT        NOT NULL,
    contract_id         BIGINT        NULL COMMENT '계약을 지워도 기록지는 남는다',
    discharge_on        DATE          NOT NULL,
    reason              VARCHAR(20)   NOT NULL COMMENT 'TRANSFER | HOSPITAL | SELF | OTHER',
    written_on          DATE          NOT NULL,
    care_grade          VARCHAR(10)   NOT NULL COMMENT '1 ~ 5 | cognitive',
    functional_status   TEXT          NOT NULL COMMENT '심신기능 상태, 암호화, 평문 2000자 이하',
    services_provided   TEXT          NOT NULL COMMENT '제공한 급여, 암호화, 평문 2000자 이하',
    after_plan          TEXT          NOT NULL COMMENT '퇴소 후 서비스 이용계획·희망급여, 암호화, 평문 1000자 이하',
    counsel_note        TEXT          NULL COMMENT '종료 상담, 암호화, 평문 1000자 이하',
    provided_on         DATE          NULL COMMENT '제공대장 — 네 칸이 함께 있거나 함께 없음',
    provided_method     VARCHAR(12)   NULL COMMENT 'VISIT | MAIL | FAX | ELECTRONIC | OTHER',
    provider_name       VARCHAR(50)   NULL,
    recipient_name      VARCHAR(512)  NULL COMMENT '수령자, 암호화(v2:) 저장, 평문 50자 이하',
    writer_name         VARCHAR(50)   NULL,
    ai_assisted         BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at          DATETIME(6)   NOT NULL,
    updated_at          DATETIME(6)   NOT NULL,

    PRIMARY KEY (id),
    UNIQUE KEY uk_lab_discharge_referrals_elder_discharge (elderly_id, discharge_on),
    KEY idx_lab_discharge_referrals_company_discharge (company_id, discharge_on),
    KEY idx_lab_discharge_referrals_contract (contract_id),
    CONSTRAINT fk_lab_discharge_referrals_company FOREIGN KEY (company_id)
        REFERENCES company (id) ON DELETE CASCADE,
    CONSTRAINT fk_lab_discharge_referrals_elderly FOREIGN KEY (elderly_id)
        REFERENCES elderly (node_id) ON DELETE CASCADE,
    CONSTRAINT fk_lab_discharge_referrals_contract FOREIGN KEY (contract_id)
        REFERENCES lab_elder_contracts (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 8) 공단 청구 대조 결과: 어르신 x 청구월 1건. status는 서버가 청구·출석·청구서 일수로 정한다
CREATE TABLE lab_claim_reviews (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    company_id     BIGINT        NOT NULL,
    elderly_id     BIGINT        NOT NULL,
    claim_month    CHAR(7)       NOT NULL COMMENT 'YYYY-MM',
    claimed_days   SMALLINT      NOT NULL COMMENT '공단 청구 일수 0~31',
    attended_days  SMALLINT      NOT NULL COMMENT '출석 일수 0~31',
    billed_days    SMALLINT      NULL COMMENT '본인부담금 청구서 일수 0~31',
    claimed_band   VARCHAR(10)   NULL COMMENT 'h3to6 | h6to8 | h8to10 | h10to13 | over13 | under3',
    status         VARCHAR(10)   NOT NULL COMMENT 'MATCH | MISMATCH | RESOLVED',
    note           VARCHAR(500)  NULL,
    reviewer_name  VARCHAR(50)   NULL,
    created_at     DATETIME(6)   NOT NULL,
    updated_at     DATETIME(6)   NOT NULL,

    PRIMARY KEY (id),
    UNIQUE KEY uk_lab_claim_reviews_elder_month (elderly_id, claim_month),
    KEY idx_lab_claim_reviews_company_month (company_id, claim_month),
    CONSTRAINT fk_lab_claim_reviews_company FOREIGN KEY (company_id)
        REFERENCES company (id) ON DELETE CASCADE,
    CONSTRAINT fk_lab_claim_reviews_elderly FOREIGN KEY (elderly_id)
        REFERENCES elderly (node_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
