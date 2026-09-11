-- 어르신 케어 정보: 요양보호사가 매일 보는 돌봄 조건(식사·투약·목욕·자리·인지·낙상).
--
-- elderly에 컬럼을 더하지 않고 표를 나눈 이유는 두 가지다.
--  1) 배차·출결 코드가 elderly를 아주 넓게 조회하는데, 그 경로마다 이 민감한 값들이 딸려 갈 이유가 없다.
--  2) 주민번호가 들어 있어 접근을 좁히려면 표 단위로 나뉘어 있는 편이 낫다.
--
-- PK를 elderly와 공유한다(@MapsId). 어르신이 지워지면 케어 정보도 같이 사라져야 한다 —
-- 주민번호가 담긴 행이 주인 없이 남는 것이 최악이라 FK에 ON DELETE CASCADE를 건다.
--
-- 주민번호와 메모 계열은 애플리케이션에서 AES-256-GCM('v2:' 접두사)으로 암호화해 넣는다.
-- 암호문이 평문의 약 4.2배가 되므로 평문 500자 기준 VARCHAR(2048)로 잡았다.
-- 그래서 이 컬럼들은 DB에서 검색·정렬할 수 없다.

CREATE TABLE elder_care_profile (
    elderly_id          BIGINT       NOT NULL COMMENT 'elderly.node_id와 공유하는 PK',

    resident_number     VARCHAR(2048) NULL COMMENT '주민등록번호 13자리(암호화)',
    birth_date          DATE          NULL,
    gender              VARCHAR(20)   NULL COMMENT 'MALE | FEMALE',
    care_grade          VARCHAR(20)   NULL COMMENT 'GRADE_1~5 | COGNITIVE_SUPPORT | NONE',

    fall_risk           BOOLEAN      NOT NULL DEFAULT FALSE COMMENT '낙상 위험',
    fall_note           VARCHAR(2048) NULL COMMENT '암호화',

    pressure_sore       BOOLEAN      NOT NULL DEFAULT FALSE COMMENT '욕창 있음/위험',
    pressure_sore_note  VARCHAR(2048) NULL COMMENT '암호화',

    diaper_type         VARCHAR(20)   NULL COMMENT 'NONE | PANTY | PAD | BOTH',
    diaper_intermittent BOOLEAN      NOT NULL DEFAULT FALSE COMMENT '간헐적 사용',

    cognition_level     VARCHAR(20)   NULL COMMENT 'NORMAL | MILD | MODERATE | SEVERE',
    cognition_note      VARCHAR(2048) NULL COMMENT '암호화',

    meal_type           VARCHAR(20)   NULL COMMENT 'REGULAR | CHOPPED | PORRIDGE | MIXED',
    -- 간식·저녁은 대부분 드신다 — 기본을 TRUE로 둬야 미입력이 '안 드심'으로 읽히지 않는다
    morning_snack       BOOLEAN      NOT NULL DEFAULT TRUE,
    afternoon_snack     BOOLEAN      NOT NULL DEFAULT TRUE,
    dinner              BOOLEAN      NOT NULL DEFAULT TRUE,
    meal_note           VARCHAR(2048) NULL COMMENT '기피식품·대체식품·기간 조건(암호화)',

    bath_time           VARCHAR(50)   NULL COMMENT '예 9:40-50',
    bath_note           VARCHAR(255)  NULL,

    med_morning         BOOLEAN      NOT NULL DEFAULT FALSE,
    med_lunch           BOOLEAN      NOT NULL DEFAULT FALSE,
    med_evening         BOOLEAN      NOT NULL DEFAULT FALSE,
    med_morning_time    VARCHAR(20)   NULL COMMENT '예 10시',
    med_lunch_time      VARCHAR(20)   NULL,
    med_evening_time    VARCHAR(20)   NULL,
    med_note            VARCHAR(2048) NULL COMMENT '암호화',

    vehicle_note        VARCHAR(255)  NULL COMMENT '이용 차량·등하원 특이사항',
    floor               INT           NULL COMMENT '자리 층',
    seat_note           VARCHAR(100)  NULL COMMENT '예 TV 앞 좌측',
    care_note           VARCHAR(2048) NULL COMMENT '기타 메모(암호화)',

    created_at          DATETIME(6)   NULL,
    modified_at         DATETIME(6)   NULL,

    PRIMARY KEY (elderly_id),
    CONSTRAINT fk_elder_care_profile_elderly FOREIGN KEY (elderly_id)
        REFERENCES elderly (node_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
