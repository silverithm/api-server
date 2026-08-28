-- 회의록 양식을 회사당 1개에서 여러 개로 확장한다.
--
-- 지금까지는 "전체회의용", "사례회의용"처럼 회의 성격별로 다른 양식(섹션 구성)을
-- 쓸 수 없었다 — meeting_minutes_templates가 회사당 1행으로 묶여 있었다(UNIQUE
-- company_id). 여기에 AI 자동 정리가 따라갈 지시(ai_instruction)와 출력 형식
-- 예시(format_example)도 양식에 함께 저장해, "이 양식을 고르면 이 말투·형식으로
-- 정리된다"가 되게 한다.
--
-- 기존 데이터는 잃지 않는다: 회사당 있던 1행이 그대로 그 회사의 '기본 양식'
-- (name='기본 양식', is_default=1)이 된다. 이미 작성된 회의록(meeting_minutes)은
-- 작성 당시 섹션을 sections_json에 스냅샷으로 이미 갖고 있어 이 변경과 무관하다.

ALTER TABLE meeting_minutes_templates
    DROP INDEX uq_meeting_minutes_templates_company,
    ADD COLUMN name VARCHAR(255) NOT NULL DEFAULT '기본 양식' COMMENT '양식 이름 (예: 전체회의용, 사례회의용)' AFTER company_id,
    ADD COLUMN ai_instruction TEXT NULL COMMENT 'AI 자동 정리가 따라갈 추가 지시 — 말투·관점 등 (예: 존댓말로, 어르신별로 묶어서)' AFTER sections,
    ADD COLUMN format_example TEXT NULL COMMENT 'AI 자동 정리가 따라갈 출력 형식 예시 (few-shot)' AFTER ai_instruction,
    ADD COLUMN is_default TINYINT(1) NOT NULL DEFAULT 0 COMMENT '회의록 작성 시 기본으로 선택되는 양식 (회사당 최대 1개)' AFTER format_example,
    ADD COLUMN sort_order INT NOT NULL DEFAULT 0 COMMENT '양식 목록 표시 순서' AFTER is_default,
    ADD KEY idx_mm_templates_company_sort (company_id, sort_order);

-- 기존에 있던 회사당 1행을 '기본 양식'으로 승격 — 데이터 유실 없이 그대로 이어받는다.
UPDATE meeting_minutes_templates SET name = '기본 양식', is_default = 1;
