-- 양식 관리 화면에서 관리자가 드래그로 조정할 수 있는 표시 순서.
-- 오름차순(작을수록 먼저 보임). 기안 작성 화면 등 양식 목록을 쓰는 모든 화면이
-- 백엔드 목록 API의 정렬(sortOrder ASC, createdAt DESC)을 그대로 따르므로
-- 프론트엔드 여러 곳을 고칠 필요가 없다.
ALTER TABLE approval_templates
    ADD COLUMN sort_order INT NOT NULL DEFAULT 0 COMMENT '양식 관리 화면 표시 순서 (오름차순)';

-- 기존 양식들의 초기값을 지금까지의 표시 순서(회사별 최신 등록순, createdAt DESC)
-- 그대로 채워 넣어 마이그레이션 직후 화면에 보이는 순서가 바뀌지 않게 한다.
SET @rn := 0;
SET @prev_company := NULL;

UPDATE approval_templates t
JOIN (
    SELECT id,
           IF(@prev_company = company_id, @rn := @rn + 1, @rn := 0) AS rn,
           @prev_company := company_id AS company_id_seen
    FROM approval_templates
    ORDER BY company_id, created_at DESC, id DESC
) ranked ON ranked.id = t.id
SET t.sort_order = ranked.rn;

CREATE INDEX idx_approval_templates_company_sort ON approval_templates (company_id, sort_order);
