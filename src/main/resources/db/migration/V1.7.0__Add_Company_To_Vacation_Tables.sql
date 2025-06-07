-- V1.7.0: Add company relationship to vacation tables

-- vacation_requests 테이블에 company_id 컬럼 추가
ALTER TABLE vacation_requests ADD COLUMN company_id BIGINT;

-- 기존 데이터에 대해 첫 번째 회사로 설정 (임시)
UPDATE vacation_requests SET company_id = (SELECT MIN(id) FROM company) WHERE company_id IS NULL;

-- NOT NULL 제약조건 추가
ALTER TABLE vacation_requests MODIFY COLUMN company_id BIGINT NOT NULL;

-- 외래키 제약조건 추가
ALTER TABLE vacation_requests ADD CONSTRAINT fk_vacation_requests_company 
    FOREIGN KEY (company_id) REFERENCES company(id);

-- 인덱스 추가
CREATE INDEX idx_vacation_requests_company_id ON vacation_requests(company_id);
CREATE INDEX idx_vacation_requests_date_company ON vacation_requests(date, company_id);
CREATE INDEX idx_vacation_requests_role_company ON vacation_requests(role, company_id);

-- vacation_limits 테이블에 company_id 컬럼 추가
ALTER TABLE vacation_limits ADD COLUMN company_id BIGINT;

-- 기존 데이터에 대해 첫 번째 회사로 설정 (임시)
UPDATE vacation_limits SET company_id = (SELECT MIN(id) FROM company) WHERE company_id IS NULL;

-- NOT NULL 제약조건 추가
ALTER TABLE vacation_limits MODIFY COLUMN company_id BIGINT NOT NULL;

-- 기존 unique constraint 제거
ALTER TABLE vacation_limits DROP INDEX uk_date_role;

-- 외래키 제약조건 추가
ALTER TABLE vacation_limits ADD CONSTRAINT fk_vacation_limits_company 
    FOREIGN KEY (company_id) REFERENCES company(id);

-- 새로운 unique constraint 추가 (회사별로 분리)
ALTER TABLE vacation_limits ADD CONSTRAINT uk_date_role_company 
    UNIQUE (date, role, company_id);

-- 인덱스 추가
CREATE INDEX idx_vacation_limits_company_id ON vacation_limits(company_id);
CREATE INDEX idx_vacation_limits_date_company ON vacation_limits(date, company_id); 