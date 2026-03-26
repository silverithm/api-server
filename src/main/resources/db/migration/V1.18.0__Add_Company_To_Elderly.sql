-- V1.18.0: Elderly 테이블에 company_id FK 추가
ALTER TABLE elderly ADD COLUMN company_id BIGINT;
ALTER TABLE elderly ADD CONSTRAINT fk_elderly_company FOREIGN KEY (company_id) REFERENCES company(id);
CREATE INDEX idx_elderly_company_id ON elderly(company_id);

-- 기존 데이터 마이그레이션: AppUser의 company_id로 백필
UPDATE elderly e
JOIN app_user u ON e.user_id = u.id
SET e.company_id = u.company_id
WHERE e.company_id IS NULL AND u.company_id IS NOT NULL;
