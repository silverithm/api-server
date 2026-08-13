-- 결재 문서에 '임시저장'(DRAFT) 상태를 더한다.
--
-- 공문은 한 번에 다 쓰기 어려워, 중간까지 적어두었다가 나중에 마저 쓰고 상신하는 경우가 많다.
-- 지금은 만드는 순간 바로 상신돼서 그런 방식이 불가능했다.
--
-- status는 VARCHAR가 아니라 MySQL ENUM이다(V1.15.1). 값을 늘리지 않고 코드에서만 DRAFT를
-- 쓰면 "Data truncated for column 'status'"로 저장 자체가 실패한다.
-- DRAFT를 맨 앞에 두는 건 표시 순서와 무관하다 — ENUM 순서는 정렬에만 영향을 주고,
-- 결재 목록은 생성일시로 정렬한다.

ALTER TABLE approval_requests
    MODIFY COLUMN status ENUM('DRAFT', 'PENDING', 'APPROVED', 'REJECTED') NOT NULL
    COMMENT '상태 (DRAFT=임시저장, PENDING, APPROVED, REJECTED)';
