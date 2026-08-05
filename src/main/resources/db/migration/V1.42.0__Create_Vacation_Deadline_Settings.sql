-- 휴무 입력 마감일 설정
--
-- 회사별로 "다음 달 휴무 입력을 매월 며칠까지 받는다"를 설정한다.
-- 마감일이 지나도 특정 날짜·직종의 휴무 신청 인원이 제한(vacation_limits.max_people)을
-- 초과한 채 남아 있으면, 그 날짜에 신청한 직원들에게 조정 요청 푸시를 매일 보낸다.

CREATE TABLE vacation_deadline_settings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    -- 매월 며칠까지 다음 달 휴무를 입력받는지 (1~31, 말일 초과 시 말일로 클램프)
    deadline_day INT NOT NULL DEFAULT 20,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP,
    modified_at TIMESTAMP,
    UNIQUE KEY uk_vacation_deadline_settings_company (company_id)
);
