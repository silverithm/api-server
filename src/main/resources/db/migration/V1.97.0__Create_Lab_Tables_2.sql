-- 케어브이 실험실 2차: 간호일지, 프로그램 일지, 직원 교육·회의, 상담일지, 욕구사정·급여제공계획.
-- 1차(V1.96.0)와 같은 규칙을 따른다 — lab_ 접두사 새 표만 더하고 기존 표는 건드리지 않는다.
-- 실험이 실패해도 이 5개 표를 DROP하면 본 서비스에는 흔적이 남지 않는다.
--
-- 모든 표에 company_id를 두고 company에 ON DELETE CASCADE를 건다. 어르신에 매인 표(간호일지·상담일지·
-- 급여제공계획)는 elderly(node_id)에도 CASCADE를 건다. 프로그램·직원 교육은 여러 사람을 묶는 기록이라
-- 어르신/직원 id를 JSON 배열로 들고 있고, 그래서 사람 FK는 없다 — 사라진 사람은 조회 때 걸러 낸다.
--
-- 건강·상담·돌봄 서술은 애플리케이션에서 AES-256-GCM('v2:' 접두사)으로 암호화해 넣는다.
-- 암호문이 평문의 약 4.2배라서 긴 글은 TEXT, 보호자 이름(평문 50자 이하)은 VARCHAR(512)로 잡았다.
-- 그래서 암호화 열은 DB에서 검색·정렬할 수 없다. 날짜·수치·분류값은 집계에 쓰이므로 암호화하지 않는다.
-- 인지기능 평가(KMMSE·CIST 등)는 새 표 없이 1차 lab_risk_assessments의 kind='COGNITIVE'(9자 ≤ VARCHAR(10))로 담는다.

-- 1) 간호일지: 어르신 1명 x 하루 1건. 바이탈 범위는 서비스가 검증하고 표는 NULL을 허용한다(안 잰 값)
CREATE TABLE lab_nursing_logs (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    company_id    BIGINT        NOT NULL,
    elderly_id    BIGINT        NOT NULL,
    log_date      DATE          NOT NULL,

    systolic      SMALLINT      NULL COMMENT '수축기 혈압 50-260',
    diastolic     SMALLINT      NULL COMMENT '이완기 혈압 30-160',
    pulse         SMALLINT      NULL COMMENT '맥박 30-200',
    temperature   DECIMAL(4,1)  NULL COMMENT '체온 34.0-42.0',
    blood_sugar   SMALLINT      NULL COMMENT '혈당 20-600',
    weight        DECIMAL(5,1)  NULL COMMENT '체중 20.0-200.0',
    med_morning   VARCHAR(10)   NULL COMMENT 'GIVEN | SKIPPED | NA',
    med_lunch     VARCHAR(10)   NULL COMMENT 'GIVEN | SKIPPED | NA',
    med_evening   VARCHAR(10)   NULL COMMENT 'GIVEN | SKIPPED | NA',
    treatment     TEXT          NULL COMMENT '처치(암호화)',
    note          TEXT          NULL COMMENT '간호 메모(암호화)',

    recorder_name VARCHAR(50)   NULL,
    ai_assisted   BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at    DATETIME(6)   NOT NULL,
    updated_at    DATETIME(6)   NOT NULL,

    PRIMARY KEY (id),
    -- 같은 날 같은 어르신 일지가 둘이 되면 안 된다 — 동시 저장의 마지막 방어선
    UNIQUE KEY uk_lab_nursing_logs_elder_date (elderly_id, log_date),
    KEY idx_lab_nursing_logs_company_date (company_id, log_date),
    CONSTRAINT fk_lab_nursing_logs_company FOREIGN KEY (company_id)
        REFERENCES company (id) ON DELETE CASCADE,
    CONSTRAINT fk_lab_nursing_logs_elderly FOREIGN KEY (elderly_id)
        REFERENCES elderly (node_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2) 프로그램 일지: 하루 활동 1건에 어르신 여러 명이 참여한다
CREATE TABLE lab_programs (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    company_id       BIGINT       NOT NULL,
    program_date     DATE         NOT NULL,
    start_time       CHAR(5)      NULL COMMENT 'HH:MM',
    category         VARCHAR(20)  NOT NULL COMMENT 'COGNITIVE | PHYSICAL | LEISURE | SOCIAL | OTHER',
    title            VARCHAR(100) NOT NULL,
    goal             TEXT         NULL COMMENT '목표(암호화)',
    content          TEXT         NULL COMMENT '진행 내용(암호화)',
    reaction         TEXT         NULL COMMENT '참여자 반응(암호화)',
    instructor_name  VARCHAR(50)  NULL,
    participant_ids  TEXT         NOT NULL COMMENT '참여 어르신 id JSON 배열(최대 100명, 모두 같은 기관)',
    ai_assisted      BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at       DATETIME(6)  NOT NULL,
    updated_at       DATETIME(6)  NOT NULL,

    PRIMARY KEY (id),
    KEY idx_lab_programs_company_date (company_id, program_date),
    CONSTRAINT fk_lab_programs_company FOREIGN KEY (company_id)
        REFERENCES company (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3) 직원 교육·회의: 법정 교육 이수와 사례회의 근거
CREATE TABLE lab_staff_trainings (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    company_id    BIGINT       NOT NULL,
    kind          VARCHAR(20)  NOT NULL COMMENT 'TRAINING | MEETING',
    held_on       DATE         NOT NULL,
    category      VARCHAR(30)  NOT NULL COMMENT '교육: ELDER_ABUSE|SEXUAL_HARASSMENT|INFECTION|SAFETY|PRIVACY|DISABILITY_AWARENESS|JOB|OTHER, 회의: CASE|STAFF|OTHER',
    topic         VARCHAR(100) NOT NULL,
    instructor    VARCHAR(50)  NULL,
    attendee_ids  TEXT         NOT NULL COMMENT '참석 직원(members.id) JSON 배열(최대 200명, 모두 같은 기관)',
    content       TEXT         NULL COMMENT '내용(암호화)',
    ai_assisted   BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,

    PRIMARY KEY (id),
    KEY idx_lab_staff_trainings_company_date (company_id, held_on),
    CONSTRAINT fk_lab_staff_trainings_company FOREIGN KEY (company_id)
        REFERENCES company (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4) 상담일지: 어르신·보호자 상담 기록
CREATE TABLE lab_consultations (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    company_id        BIGINT        NOT NULL,
    elderly_id        BIGINT        NOT NULL,
    consulted_on      DATE          NOT NULL,
    counterpart       VARCHAR(20)   NOT NULL COMMENT 'SELF | GUARDIAN | FAMILY | OTHER',
    counterpart_name  VARCHAR(512)  NULL COMMENT '상담 상대 이름(암호화, 평문 50자 이하)',
    method            VARCHAR(20)   NOT NULL COMMENT 'VISIT | PHONE | ONLINE | OTHER',
    content           TEXT          NULL COMMENT '상담 내용(암호화)',
    follow_up         TEXT          NULL COMMENT '후속 조치(암호화)',
    consultant_name   VARCHAR(50)   NULL,
    ai_assisted       BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at        DATETIME(6)   NOT NULL,

    PRIMARY KEY (id),
    KEY idx_lab_consultations_elder_date (elderly_id, consulted_on),
    KEY idx_lab_consultations_company_date (company_id, consulted_on),
    CONSTRAINT fk_lab_consultations_company FOREIGN KEY (company_id)
        REFERENCES company (id) ON DELETE CASCADE,
    CONSTRAINT fk_lab_consultations_elderly FOREIGN KEY (elderly_id)
        REFERENCES elderly (node_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5) 욕구사정·급여제공계획: 어르신 1명 x 연도 1건
CREATE TABLE lab_care_plans (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    company_id          BIGINT       NOT NULL,
    elderly_id          BIGINT       NOT NULL,
    plan_year           SMALLINT     NOT NULL,
    assessed_on         DATE         NOT NULL,
    needs               TEXT         NULL COMMENT '욕구사정 JSON {physical,cognition,nutrition,communication,family,other}(암호화)',
    goals               TEXT         NULL COMMENT '목표(암호화)',
    services            TEXT         NULL COMMENT '급여제공 내용 JSON [{area,content,frequency}] 30개 이하(암호화)',
    guardian_agreed_on  DATE         NULL,
    writer_name         VARCHAR(50)  NULL,
    ai_assisted         BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at          DATETIME(6)  NOT NULL,
    updated_at          DATETIME(6)  NOT NULL,

    PRIMARY KEY (id),
    UNIQUE KEY uk_lab_care_plans_elder_year (elderly_id, plan_year),
    KEY idx_lab_care_plans_company_year (company_id, plan_year),
    CONSTRAINT fk_lab_care_plans_company FOREIGN KEY (company_id)
        REFERENCES company (id) ON DELETE CASCADE,
    CONSTRAINT fk_lab_care_plans_elderly FOREIGN KEY (elderly_id)
        REFERENCES elderly (node_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
