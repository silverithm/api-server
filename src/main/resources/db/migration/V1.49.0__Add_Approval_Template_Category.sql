-- 결재 양식 대분류(공문/교육/인사 등) — 기관이 자유롭게 지정하는 문자열
ALTER TABLE approval_templates ADD COLUMN category VARCHAR(50) NULL;
