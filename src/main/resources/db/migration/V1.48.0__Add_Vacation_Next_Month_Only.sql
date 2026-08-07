-- 휴무 신청 가능 기간을 "바로 다음 달"로 제한하는 설정.
--
-- 켜면 직원은 다음 달에 속한 날짜만 휴무를 신청할 수 있다. 근무표를 달 단위로 짜는
-- 기관에서 몇 달 뒤 휴무가 미리 들어와 표가 흔들리는 걸 막는 용도다.
-- 관리자가 대신 등록하는 경로는 제한받지 않는다.
ALTER TABLE vacation_deadline_settings
    ADD COLUMN next_month_only BOOLEAN NOT NULL DEFAULT FALSE;
