-- 케어브이 실험실 3차: 주간 식단표, 위생·안전·시설 점검 기록.
-- 1·2차(V1.96.0·V1.97.0)와 같은 규칙 — lab_ 접두사 새 표만 더하고 기존 표는 건드리지 않는다.
-- 실험이 실패해도 이 2개 표를 DROP하면 본 서비스에는 흔적이 남지 않는다.
--
-- 두 표 모두 기관 단위 기록이라 어르신 FK가 없고, 개인정보가 없어 암호화하지 않는다.
-- company_id는 company에 ON DELETE CASCADE로 묶는다.

-- 1) 주간 식단: 기관 1곳 x 주(월요일 시작) 1건. days는 월~토 6일 JSON 배열(칸마다 60자 이하, 서비스가 검증)
CREATE TABLE lab_meal_plans (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    company_id  BIGINT       NOT NULL,
    week_start  DATE         NOT NULL COMMENT '그 주 월요일',
    days        TEXT         NOT NULL COMMENT '6일 JSON 배열 [{date, lunch:{rice,soup,main,side1,side2,kimchi}, amSnack, pmSnack, soft}]',
    notes       TEXT         NULL COMMENT '식단 메모(1000자 이하)',
    writer_name VARCHAR(50)  NULL,
    ai_assisted BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  NOT NULL,

    PRIMARY KEY (id),
    -- 같은 주 식단이 둘이 되면 안 된다 — 동시 저장의 마지막 방어선
    UNIQUE KEY uk_lab_meal_plans_company_week (company_id, week_start),
    CONSTRAINT fk_lab_meal_plans_company FOREIGN KEY (company_id)
        REFERENCES company (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2) 위생·안전·시설 점검: 기관 1곳 x 종류 x 날짜 1건
CREATE TABLE lab_safety_checks (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    company_id     BIGINT       NOT NULL,
    check_date     DATE         NOT NULL,
    kind           VARCHAR(20)  NOT NULL COMMENT 'KITCHEN | HYGIENE | FIRE | FACILITY | VEHICLE | INFECTION | DRILL',
    items          TEXT         NOT NULL COMMENT '점검 항목 JSON 배열 [{code, ok: true|false|null, note}] (60개 이하)',
    actions        TEXT         NULL COMMENT '조치 내용(2000자 이하)',
    inspector_name VARCHAR(50)  NULL,
    ai_assisted    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at     DATETIME(6)  NOT NULL,
    updated_at     DATETIME(6)  NOT NULL,

    PRIMARY KEY (id),
    UNIQUE KEY uk_lab_safety_checks_company_kind_date (company_id, kind, check_date),
    -- 월·연도 조회는 기관 + 날짜 범위로 읽는다
    KEY idx_lab_safety_checks_company_date (company_id, check_date),
    CONSTRAINT fk_lab_safety_checks_company FOREIGN KEY (company_id)
        REFERENCES company (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
