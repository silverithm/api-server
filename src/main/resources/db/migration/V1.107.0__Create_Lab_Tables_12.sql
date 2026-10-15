-- 케어브이 실험실 12차: 급여제공기록지 법정 서식 칸, 프로그램 인지활동형 칸, 시설 정보, 시설 관리 대장 실시 기록.
-- 1~11차(V1.96.0~V1.106.0)와 같은 규칙 — 실험실 표(lab_care_records, lab_programs)에 열만 더하고
-- lab_ 접두사 새 표 둘을 만든다. 실험실 밖 표는 건드리지 않는다.
-- 실험이 실패해도 새 표 둘과 더한 열을 지우면 본 서비스에는 흔적이 남지 않는다.
--
-- 모든 새 표는 company_id를 company에 ON DELETE CASCADE로 묶는다.

-- 1) 급여제공기록: 별지 제15호 서식 칸 JSON(암호화). 옛 기록은 NULL.
ALTER TABLE lab_care_records
    ADD COLUMN form TEXT NULL COMMENT '법정 서식 칸 JSON(CareRecordForm), 암호화. 옛 기록은 NULL';

-- 2) 프로그램 일지: 시간(분)·인지활동형 여부·제공자 유형
ALTER TABLE lab_programs
    ADD COLUMN duration_minutes   SMALLINT    NULL COMMENT '10~480분',
    ADD COLUMN cognitive_activity BOOLEAN     NOT NULL DEFAULT FALSE COMMENT '인지활동형 프로그램 여부',
    ADD COLUMN provider_kind      VARCHAR(20) NULL COMMENT 'PROGRAM_MANAGER | DEMENTIA_CAREGIVER | EXTERNAL_INSTRUCTOR | OTHER, 인지활동형이면 필수';

-- 3) 시설 정보 — 기관당 한 줄
CREATE TABLE lab_facility_profiles (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    company_id        BIGINT       NOT NULL,
    approved_on       DATE         NULL COMMENT '사용승인일, 미래 불가',
    gross_area_m2     INT          NULL COMMENT '연면적 1~1,000,000',
    sprinkler         VARCHAR(8)   NOT NULL DEFAULT 'UNKNOWN' COMMENT 'NONE | SIMPLE | FULL | UNKNOWN',
    power_kind        VARCHAR(10)  NOT NULL DEFAULT 'UNKNOWN' COMMENT 'GENERAL 일반용(저압 75kW 미만) | SELF_USE | UNKNOWN',
    gas_kind          VARCHAR(8)   NOT NULL DEFAULT 'UNKNOWN' COMMENT 'NONE | CITY | LPG | UNKNOWN',
    lpg_capacity_kg   INT          NULL COMMENT '0~100,000, LPG가 아니면 비운다',
    elevator          VARCHAR(10)  NOT NULL DEFAULT 'UNKNOWN' COMMENT 'NONE | CENTER 센터가 관리주체 | BUILDING 건물 관리주체 | UNKNOWN',
    water_tank        VARCHAR(8)   NOT NULL DEFAULT 'UNKNOWN' COMMENT 'NONE | LARGE 수도법 대형건축물등 | SMALL | UNKNOWN',
    septic            VARCHAR(8)   NOT NULL DEFAULT 'UNKNOWN' COMMENT 'NONE | YES | UNKNOWN',
    applies_override  JSON         NOT NULL COMMENT '{항목 코드: YES|NO} 직접 지정(모르는 코드·값 불가)',
    fire_manager_name VARCHAR(50)  NULL,
    note              VARCHAR(500) NULL,
    writer_name       VARCHAR(50)  NULL COMMENT '서버가 사람 이름으로 채운다',
    created_at        DATETIME(6)  NOT NULL,
    updated_at        DATETIME(6)  NOT NULL,

    PRIMARY KEY (id),
    UNIQUE KEY uk_lab_facility_profiles_company (company_id),
    CONSTRAINT fk_lab_facility_profiles_company FOREIGN KEY (company_id)
        REFERENCES company (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4) 시설 점검·청소·검사·보험 실시 기록 1건
CREATE TABLE lab_facility_logs (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    company_id    BIGINT       NOT NULL,
    item_code     VARCHAR(30)  NOT NULL COMMENT '항목 코드 19개 중 하나(서비스가 확인)',
    done_on       DATE         NOT NULL COMMENT '미래 불가',
    done_by       VARCHAR(8)   NOT NULL COMMENT 'SELF | VENDOR | AGENCY',
    vendor_name   VARCHAR(100) NULL,
    result        VARCHAR(10)  NOT NULL COMMENT 'PASS | FIX_NEEDED | FAIL',
    action_note   VARCHAR(500) NULL COMMENT '조치 내용',
    reported_on   DATE         NULL COMMENT '소방 자체점검 결과 보고일, FIRE_OPERATION·FIRE_COMPREHENSIVE만, done_on 이상',
    valid_until   DATE         NULL COMMENT '보험 만기, 보험 두 항목은 필수이고 done_on보다 뒤',
    doc_location  VARCHAR(200) NULL COMMENT '결과서·증명서 보관 위치',
    writer_name   VARCHAR(50)  NULL COMMENT '서버가 사람 이름으로 채운다',
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,

    PRIMARY KEY (id),
    KEY idx_lab_facility_logs_company_item_done (company_id, item_code, done_on),
    CONSTRAINT fk_lab_facility_logs_company FOREIGN KEY (company_id)
        REFERENCES company (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
