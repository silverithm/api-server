-- 휴무 입력 마감일: 월별 직접 지정.
--
-- 매월 고정일(vacation_deadline_settings.deadline_day)만으로는 "셋째 주 일요일"처럼
-- 달마다 달라지는 마감일을 표현할 수 없다. 이 테이블의 행이 있는 달은 그 날짜가
-- 고정일보다 우선하는 마감일이 된다 (행이 없는 달은 기존 고정일 사용).

CREATE TABLE vacation_deadline_dates (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    -- 마감일이 속한 달 (yyyy-MM) — 이 달의 마감일을 덮어쓴다
    target_month VARCHAR(7) NOT NULL,
    deadline_date DATE NOT NULL,
    created_at TIMESTAMP,
    modified_at TIMESTAMP,
    UNIQUE KEY uk_vacation_deadline_dates_company_month (company_id, target_month)
);
