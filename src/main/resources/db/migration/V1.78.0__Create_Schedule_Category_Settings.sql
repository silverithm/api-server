-- 기본 일정 구분(회의·행사·교육·기타)의 기관별 커스터마이징.
--
-- 기본 구분은 enum이라 지울 수 없고 기존 일정들이 category로 물고 있으므로,
-- 기관별로 이름·색을 덮어쓰거나 등록 폼에서 숨기는 설정만 저장한다.
-- 행이 없거나 필드가 NULL이면 enum 기본값(ScheduleCategory)을 쓴다.
CREATE TABLE schedule_category_settings (
    id BIGINT NOT NULL AUTO_INCREMENT,
    company_id BIGINT NOT NULL,
    category VARCHAR(20) NOT NULL COMMENT 'ScheduleCategory enum 이름 (MEETING 등)',
    display_name VARCHAR(50) NULL COMMENT '기관이 바꾼 이름. NULL이면 기본 이름',
    color VARCHAR(7) NULL COMMENT '기관이 바꾼 색(hex). NULL이면 enum 기본색',
    hidden BIT(1) NOT NULL DEFAULT 0 COMMENT '새 일정 등록 폼에서 숨김 (기존 일정 표시는 유지)',
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_schedcat_company_category (company_id, category),
    CONSTRAINT fk_schedcat_company FOREIGN KEY (company_id) REFERENCES company (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
