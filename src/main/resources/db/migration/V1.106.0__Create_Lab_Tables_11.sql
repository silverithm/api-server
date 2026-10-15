-- 케어브이 실험실 11차: 감염병 유행·발생 기록, 직원 복지(포상) 제공대장, 평가 가산금 수령·사용, 급여제공계획 변경 이력.
-- 1~10차(V1.96.0~V1.105.0)와 같은 규칙 — lab_ 접두사 새 표만 더하고 기존 표는 건드리지 않는다.
-- 욕구사정의 새 칸(질병·구강·주관적 욕구·자원이용·총평·기피식품)은 lab_care_plans.needs(암호화 JSON)에 키만 더하므로 표를 바꾸지 않는다.
-- 실험이 실패해도 이 4개 표를 DROP하면 본 서비스에는 흔적이 남지 않는다
-- (직원 id는 member와 FK 없이 서비스가 같은 기관인지 확인한다. 어르신 id는 elderly에 연쇄 삭제로 묶는다).
--
-- 모든 표는 company_id를 company에 ON DELETE CASCADE로 묶는다.

-- 1) 감염병 유행·발생·국가 위기 경보 기록 1건
CREATE TABLE lab_outbreaks (
    id                        BIGINT        NOT NULL AUTO_INCREMENT,
    company_id                BIGINT        NOT NULL,
    kind                      VARCHAR(10)   NOT NULL COMMENT 'OUTBREAK 기관 내 유행 | CASE 확진·의심 발생 | NATIONAL 국가 위기 경보',
    disease                   VARCHAR(50)   NOT NULL COMMENT '병명·증상',
    started_on                DATE          NOT NULL COMMENT '미래 불가',
    elder_cases               SMALLINT      NOT NULL DEFAULT 0 COMMENT '0~500',
    staff_cases               SMALLINT      NOT NULL DEFAULT 0 COMMENT '0~500',
    guardian_notified_on      DATE          NULL COMMENT 'started_on 이상',
    health_center_reported_on DATE          NULL COMMENT 'started_on 이상',
    authority_reported_on     DATE          NULL COMMENT '지자체·공단 보고, started_on 이상',
    alert_level               VARCHAR(8)    NULL COMMENT 'NATIONAL이면 BLUE|YELLOW|ORANGE|RED 필수, 그 밖이면 비움',
    actions                   JSON          NOT NULL COMMENT '조치 키 배열(중복·모르는 키 불가)',
    ended_on                  DATE          NULL COMMENT 'started_on 이상',
    note                      TEXT          NULL COMMENT '암호화(평문 1,000자 이하)',
    writer_name               VARCHAR(50)   NULL COMMENT '서버가 사람 이름으로 채운다',
    created_at                DATETIME(6)   NOT NULL,
    updated_at                DATETIME(6)   NOT NULL,

    PRIMARY KEY (id),
    KEY idx_lab_outbreaks_company_started (company_id, started_on),
    CONSTRAINT fk_lab_outbreaks_company FOREIGN KEY (company_id)
        REFERENCES company (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2) 직원 복지(포상) 제공대장 1건
CREATE TABLE lab_staff_welfare (
    id                  BIGINT        NOT NULL AUTO_INCREMENT,
    company_id          BIGINT        NOT NULL,
    given_on            DATE          NOT NULL COMMENT '미래 불가',
    member_id           BIGINT        NOT NULL COMMENT '같은 기관 직원(서비스가 확인)',
    kind                VARCHAR(20)   NOT NULL COMMENT 'GIFT_CARD | BOOKS | ... | DINNER | PLAQUE_ONLY | DONATED_GOODS',
    item                VARCHAR(100)  NOT NULL COMMENT '포상품·내용',
    amount              BIGINT        NOT NULL DEFAULT 0 COMMENT '0~100,000,000',
    signed              BOOLEAN       NOT NULL DEFAULT FALSE COMMENT '수령인 서명 받음',
    recipient_is_owner  BOOLEAN       NOT NULL DEFAULT FALSE COMMENT '대표자 본인',
    evidence_ledger_id  BIGINT        NULL COMMENT '같은 기관 회계 장부 기록(서비스가 확인)',
    evidence_note       VARCHAR(200)  NULL COMMENT '영수증·이체내역 보관 위치',
    note                VARCHAR(200)  NULL,
    created_at          DATETIME(6)   NOT NULL,
    updated_at          DATETIME(6)   NOT NULL,

    PRIMARY KEY (id),
    KEY idx_lab_staff_welfare_company_given (company_id, given_on),
    CONSTRAINT fk_lab_staff_welfare_company FOREIGN KEY (company_id)
        REFERENCES company (id) ON DELETE CASCADE,
    CONSTRAINT fk_lab_staff_welfare_ledger FOREIGN KEY (evidence_ledger_id)
        REFERENCES lab_ledger_entries (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3) 평가 가산금 수령·사용 1건
CREATE TABLE lab_bonus_funds (
    id                  BIGINT        NOT NULL AUTO_INCREMENT,
    company_id          BIGINT        NOT NULL,
    kind                VARCHAR(8)    NOT NULL COMMENT 'RECEIVED | SPENT',
    occurred_on         DATE          NOT NULL COMMENT '미래 불가',
    amount              BIGINT        NOT NULL COMMENT '1~1,000,000,000',
    member_id           BIGINT        NULL COMMENT 'SPENT 대상 직원(같은 기관), null이면 여러 직원',
    purpose             VARCHAR(100)  NOT NULL,
    owner_use           BOOLEAN       NOT NULL DEFAULT FALSE COMMENT '대표자 겸 시설장·직원에게 쓴 금액 — 비율에서 뺀다',
    evidence_ledger_id  BIGINT        NULL COMMENT '같은 기관 회계 장부 기록(서비스가 확인)',
    note                VARCHAR(200)  NULL,
    created_at          DATETIME(6)   NOT NULL,
    updated_at          DATETIME(6)   NOT NULL,

    PRIMARY KEY (id),
    KEY idx_lab_bonus_funds_company_occurred (company_id, occurred_on),
    CONSTRAINT fk_lab_bonus_funds_company FOREIGN KEY (company_id)
        REFERENCES company (id) ON DELETE CASCADE,
    CONSTRAINT fk_lab_bonus_funds_ledger FOREIGN KEY (evidence_ledger_id)
        REFERENCES lab_ledger_entries (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4) 급여제공계획 변경 이력 1건
CREATE TABLE lab_care_plan_changes (
    id                    BIGINT        NOT NULL AUTO_INCREMENT,
    company_id            BIGINT        NOT NULL,
    elderly_id            BIGINT        NOT NULL COMMENT '같은 기관 어르신(서비스가 확인)',
    changed_on            DATE          NOT NULL COMMENT '미래 불가',
    reason                VARCHAR(16)   NOT NULL COMMENT 'FUNCTION_CHANGE | ELDER_WISH | GUARDIAN_REQUEST | GRADE_CHANGE | OTHER',
    reason_note           VARCHAR(300)  NOT NULL,
    change_items          JSON          NOT NULL COMMENT 'CONTENT|TIME|FREQUENCY 1~3개 배열, 중복 불가',
    change_detail         TEXT          NOT NULL COMMENT '암호화(평문 1,000자 이하)',
    event_id              BIGINT        NULL COMMENT '같은 기관·같은 어르신 상태변화 기록(서비스가 확인)',
    guardian_explained_on DATE          NULL COMMENT 'changed_on 이상',
    guardian_signed_on    DATE          NULL COMMENT 'changed_on 이상',
    notified_on           DATE          NULL COMMENT '공단 통보일, changed_on 이상',
    writer_name           VARCHAR(50)   NULL COMMENT '서버가 사람 이름으로 채운다',
    created_at            DATETIME(6)   NOT NULL,
    updated_at            DATETIME(6)   NOT NULL,

    PRIMARY KEY (id),
    KEY idx_lab_care_plan_changes_elder_changed (company_id, elderly_id, changed_on),
    CONSTRAINT fk_lab_care_plan_changes_company FOREIGN KEY (company_id)
        REFERENCES company (id) ON DELETE CASCADE,
    CONSTRAINT fk_lab_care_plan_changes_elderly FOREIGN KEY (elderly_id)
        REFERENCES elderly (node_id) ON DELETE CASCADE,
    CONSTRAINT fk_lab_care_plan_changes_event FOREIGN KEY (event_id)
        REFERENCES lab_care_events (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
