-- 케어브이 실험실 10차: 예방접종, 사례관리 회의, 송영 운행일지, 송영 차량·보험, 수칙·운행표 제공 기록, 규정·지침 대장.
-- 1~9차(V1.96.0~V1.104.0)와 같은 규칙 — lab_ 접두사 새 표만 더하고 기존 표는 건드리지 않는다.
-- 실험이 실패해도 이 6개 표를 DROP하면 본 서비스에는 흔적이 남지 않는다
-- (직원 id는 표끼리·member와 FK 없이 서비스가 같은 기관인지 확인한다. 어르신 id는 elderly에 연쇄 삭제로 묶는다).
--
-- 모든 표는 company_id를 company에 ON DELETE CASCADE로 묶는다.

-- 1) 예방접종: 어르신 x 연도 x 백신 1건
CREATE TABLE lab_vaccinations (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    company_id   BIGINT        NOT NULL,
    elderly_id   BIGINT        NOT NULL COMMENT '같은 기관 어르신(서비스가 확인)',
    season_year  SMALLINT      NOT NULL COMMENT '2020~2100, 접종 연도(달력 연도)',
    vaccine      VARCHAR(16)   NOT NULL COMMENT 'INFLUENZA | PNEUMOCOCCAL | COVID19',
    status       VARCHAR(10)   NOT NULL COMMENT 'DONE | REFUSED | EXCLUDED | UNKNOWN',
    given_on     DATE          NULL COMMENT 'DONE이면 필수·season_year와 같은 해·미래 불가, 그 밖이면 비움',
    institution  VARCHAR(100)  NULL,
    verified_by  VARCHAR(12)   NULL COMMENT 'CERT | GUARDIAN | NIP | OTHER - DONE이면 필수',
    reason       VARCHAR(200)  NULL COMMENT 'REFUSED·EXCLUDED면 필수',
    note         VARCHAR(200)  NULL,
    writer_name  VARCHAR(50)   NULL COMMENT '서버가 로그인 사용자로 채운다',
    created_at   DATETIME(6)   NOT NULL,
    updated_at   DATETIME(6)   NOT NULL,

    PRIMARY KEY (id),
    UNIQUE KEY uk_lab_vaccinations_elder_season_vaccine (company_id, elderly_id, season_year, vaccine),
    KEY idx_lab_vaccinations_company_season (company_id, season_year),
    CONSTRAINT fk_lab_vaccinations_company FOREIGN KEY (company_id)
        REFERENCES company (id) ON DELETE CASCADE,
    CONSTRAINT fk_lab_vaccinations_elderly FOREIGN KEY (elderly_id)
        REFERENCES elderly (node_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2) 사례관리 회의: 회의 1건. 참석자·의견·대상은 JSON(회의록만 암호화)
CREATE TABLE lab_case_meetings (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    company_id    BIGINT        NOT NULL,
    held_on       DATE          NOT NULL COMMENT '미래 불가',
    attendee_ids  JSON          NOT NULL COMMENT '같은 기관 직원 id 배열 1~30명, 중복 없음',
    opinions      JSON          NOT NULL COMMENT '[{memberId, opinion|null}] memberId는 참석자 안, 참석자마다 최대 1개',
    targets       JSON          NOT NULL COMMENT '1~10명 [{elderlyId, reason, plan, ownerMemberId, reflectedOn, reflectMethod, reflectNote, evaluatedOn, evaluatorName, resolved, nextAction}]',
    minutes       TEXT          NULL COMMENT '암호화(평문 3,000자 이하)',
    writer_name   VARCHAR(50)   NULL,
    ai_assisted   BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at    DATETIME(6)   NOT NULL,
    updated_at    DATETIME(6)   NOT NULL,

    PRIMARY KEY (id),
    KEY idx_lab_case_meetings_company_held (company_id, held_on),
    CONSTRAINT fk_lab_case_meetings_company FOREIGN KEY (company_id)
        REFERENCES company (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3) 송영 운행일지(고시 제34조 제4항): 날짜 x 등원/하원 x 차량 1건
CREATE TABLE lab_transport_logs (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    company_id        BIGINT        NOT NULL,
    run_date          DATE          NOT NULL COMMENT '미래 불가',
    direction         VARCHAR(8)    NOT NULL COMMENT 'PICKUP | DROPOFF',
    vehicle_no        VARCHAR(20)   NOT NULL,
    driver_member_id  BIGINT        NOT NULL COMMENT '같은 기관 직원(서비스가 확인)',
    escort_member_id  BIGINT        NULL COMMENT '같은 기관 직원, 운전원과 같으면 400',
    depart_time       TIME          NULL,
    arrive_time       TIME          NULL COMMENT 'depart_time 이상',
    riders            JSON          NOT NULL COMMENT '1~30명 [{elderlyId, boarded, boardTime, alightTime, note}] 시각은 HH:mm 문자열',
    note              VARCHAR(300)  NULL,
    writer_name       VARCHAR(50)   NULL,
    created_at        DATETIME(6)   NOT NULL,
    updated_at        DATETIME(6)   NOT NULL,

    PRIMARY KEY (id),
    UNIQUE KEY uk_lab_transport_logs_run (company_id, run_date, direction, vehicle_no),
    CONSTRAINT fk_lab_transport_logs_company FOREIGN KEY (company_id)
        REFERENCES company (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4) 송영 차량·보험: 기관 x 차량번호 1건
CREATE TABLE lab_transport_vehicles (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    company_id      BIGINT        NOT NULL,
    vehicle_no      VARCHAR(20)   NOT NULL,
    label           VARCHAR(50)   NULL COMMENT '차종',
    insurer         VARCHAR(50)   NULL,
    policy_no       VARCHAR(40)   NULL,
    insurance_from  DATE          NULL,
    insurance_to    DATE          NULL COMMENT 'insurance_from 이상',
    comprehensive   BOOLEAN       NOT NULL DEFAULT FALSE COMMENT '자동차종합보험 - 책임보험만이면 false',
    active          BOOLEAN       NOT NULL DEFAULT TRUE,
    note            VARCHAR(200)  NULL,
    created_at      DATETIME(6)   NOT NULL,
    updated_at      DATETIME(6)   NOT NULL,

    PRIMARY KEY (id),
    UNIQUE KEY uk_lab_transport_vehicles_no (company_id, vehicle_no),
    CONSTRAINT fk_lab_transport_vehicles_company FOREIGN KEY (company_id)
        REFERENCES company (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5) 수칙·운행표 제공 기록: 어르신 x 제공 1건
CREATE TABLE lab_guide_deliveries (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    company_id    BIGINT        NOT NULL,
    elderly_id    BIGINT        NOT NULL COMMENT '같은 기관 어르신(서비스가 확인)',
    kind          VARCHAR(16)   NOT NULL COMMENT 'TRANSPORT_RULES | TIMETABLE',
    delivered_on  DATE          NOT NULL COMMENT '미래 불가',
    method        VARCHAR(8)    NOT NULL COMMENT 'FACE | PAPER | SMS | SNS | OTHER',
    note          VARCHAR(200)  NULL,
    created_at    DATETIME(6)   NOT NULL,
    updated_at    DATETIME(6)   NOT NULL,

    PRIMARY KEY (id),
    KEY idx_lab_guide_deliveries_company_kind (company_id, kind, delivered_on),
    KEY idx_lab_guide_deliveries_elderly (elderly_id),
    CONSTRAINT fk_lab_guide_deliveries_company FOREIGN KEY (company_id)
        REFERENCES company (id) ON DELETE CASCADE,
    CONSTRAINT fk_lab_guide_deliveries_elderly FOREIGN KEY (elderly_id)
        REFERENCES elderly (node_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 6) 규정·지침 대장: 같은 kind(+guide_no)는 하나(OTHER 제외) - 유일 키 대신 서비스가 기관 행 잠금으로 확인한다
CREATE TABLE lab_policy_docs (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    company_id  BIGINT        NOT NULL,
    kind        VARCHAR(20)   NOT NULL COMMENT 'OPERATING_RULES | CARE_GUIDE | PRIVACY_POLICY | INFECTION_PLAN | TRANSPORT_RULES | GRIEVANCE_GUIDE | OTHER',
    guide_no    TINYINT       NULL COMMENT 'CARE_GUIDE면 1~12 필수, 그 밖이면 비움',
    title       VARCHAR(100)  NOT NULL,
    revised_on  DATE          NULL COMMENT '미래 불가',
    location    VARCHAR(100)  NULL COMMENT '비치 장소',
    checks      JSON          NOT NULL COMMENT '항목 키 -> boolean, 키 30개 이하·키 40자 이하, 키는 영문·숫자·_',
    trained_on  DATE          NULL COMMENT '직원 교육일, 미래 불가',
    note        VARCHAR(300)  NULL,
    created_at  DATETIME(6)   NOT NULL,
    updated_at  DATETIME(6)   NOT NULL,

    PRIMARY KEY (id),
    KEY idx_lab_policy_docs_company_kind (company_id, kind, guide_no),
    CONSTRAINT fk_lab_policy_docs_company FOREIGN KEY (company_id)
        REFERENCES company (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
