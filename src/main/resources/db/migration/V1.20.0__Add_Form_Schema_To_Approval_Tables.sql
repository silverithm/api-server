-- approval_templates에 폼 스키마 추가
ALTER TABLE approval_templates ADD COLUMN template_type VARCHAR(10) NOT NULL DEFAULT 'file';
ALTER TABLE approval_templates ADD COLUMN form_schema JSON NULL;
ALTER TABLE approval_templates MODIFY COLUMN file_url VARCHAR(255) NULL;
ALTER TABLE approval_templates MODIFY COLUMN file_name VARCHAR(255) NULL;
ALTER TABLE approval_templates MODIFY COLUMN file_size BIGINT NULL;

-- approval_requests에 폼 데이터 추가
ALTER TABLE approval_requests ADD COLUMN form_data JSON NULL;
