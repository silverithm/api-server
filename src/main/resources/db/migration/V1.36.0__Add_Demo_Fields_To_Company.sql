-- 체험하기(데모) 기능: 방문자별 격리 데모 기관 식별용 컬럼
ALTER TABLE company ADD COLUMN is_demo BOOLEAN NOT NULL DEFAULT FALSE COMMENT '체험하기로 생성된 데모 기관 여부';
ALTER TABLE company ADD COLUMN demo_expires_at DATETIME NULL COMMENT '데모 기관 만료(자동 삭제 대상) 시각';

CREATE INDEX idx_company_demo_expires ON company (is_demo, demo_expires_at);
