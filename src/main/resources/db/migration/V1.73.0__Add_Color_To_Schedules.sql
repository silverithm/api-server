-- 일정 라벨 색이 아니라 일정 자체가 색을 갖도록 전환하는 첫 단계.
--
-- null이면 카테고리 기본색(웹 SCHEDULE_CATEGORY_COLORS)으로 폴백하는 기존 동작을 그대로 유지한다.
-- 다음 마이그레이션(V1.74.0)에서 기존 label 색을 이 컬럼으로 백필한다.
ALTER TABLE schedules
    ADD COLUMN color VARCHAR(7) NULL COMMENT '일정 색상 코드 (hex, null이면 카테고리 기본색으로 폴백)' AFTER label_id;
