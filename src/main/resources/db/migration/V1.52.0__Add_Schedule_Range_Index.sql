-- 연간일정(1년치 한 번에 조회)이 느린 원인 보강.
--
-- 목록 조회 조건은 "회사 + 기간이 겹치는 일정"이고 정렬은 start_date다.
-- 기존 인덱스는 company_id / start_date / end_date가 따로 있어, OR로 묶인 기간 조건에서는
-- 회사 인덱스로 해당 회사 일정을 전부 읽은 뒤 필터링하고 다시 정렬(filesort)하게 된다.
-- 한 달치는 티가 안 나지만 1년치는 그대로 체감이 된다.
--
-- (company_id, start_date) 복합 인덱스를 두면 회사로 좁히면서 start_date 순서까지
-- 인덱스가 보장해 정렬 비용이 사라진다. end_date 쪽 조건을 위해 짝이 되는 인덱스도 같이 만든다.
CREATE INDEX idx_schedules_company_start ON schedules(company_id, start_date);
CREATE INDEX idx_schedules_company_end ON schedules(company_id, end_date);
