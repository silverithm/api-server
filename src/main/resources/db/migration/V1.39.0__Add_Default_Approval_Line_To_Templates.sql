-- 양식별 기본 결재선: 기안 시 이 결재선이 자동으로 채워진다 (기안자가 수정 가능)
-- 형식: [{"approverType":"ADMIN|MEMBER","approverId":123,"name":"김하늘","position":"시설장"}]
ALTER TABLE approval_templates
    ADD COLUMN default_approval_line JSON NULL COMMENT '기본 결재선(기안 시 프리필)';
