-- V1.73에서 추가한 schedules.color를 기존 라벨 색으로 채운다.
--
-- label_id가 있는 일정만 대상이고, 이미 color가 채워진 행은 건드리지 않는다
-- (이 마이그레이션 이후 애플리케이션이 색을 먼저 저장하는 경로가 생겨도 안전하게 재실행 가능).
UPDATE schedules s
    JOIN schedule_labels l ON l.id = s.label_id
SET s.color = l.color
WHERE s.label_id IS NOT NULL
  AND (s.color IS NULL OR s.color = '');
