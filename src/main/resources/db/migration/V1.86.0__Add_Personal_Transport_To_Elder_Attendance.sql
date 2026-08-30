-- V1.86.0: 어르신 출결에 개인등원/개인하원 구분 추가
--
-- 보호자가 직접 데려오거나(개인등원) 데려가는(개인하원) 경우가 있다.
-- 출석은 했으므로 status=PRESENT이지만 해당 방향의 배차표에서는 빠져야 한다.
-- "개인등원 + 차량하원" 조합이 실제로 존재하므로 status enum이 아닌 별도 플래그로 둔다.

ALTER TABLE elder_attendance
    ADD COLUMN personal_pickup  BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN personal_dropoff BOOLEAN NOT NULL DEFAULT FALSE;
