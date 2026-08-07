-- 근무조정 중요 행사.
--
-- 관리자가 "이 날은 행사가 있으니 휴무를 피해달라"고 알리는 용도.
-- 월간일정(schedules)과 달리 휴무 달력 위에 겹쳐 보여주는 가벼운 표시라 별도 테이블로 둔다.

CREATE TABLE vacation_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    title VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    start_date DATE NOT NULL,
    -- 하루짜리 행사는 start_date와 같다
    end_date DATE NOT NULL,
    -- 휴무 신청 시 경고를 띄울지 (끄면 달력 표시만)
    warn_on_request BOOLEAN NOT NULL DEFAULT TRUE,
    created_by VARCHAR(255),
    created_at TIMESTAMP,
    modified_at TIMESTAMP,
    KEY idx_vacation_events_company_date (company_id, start_date, end_date)
);
