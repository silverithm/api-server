ALTER TABLE company
    ADD COLUMN company_code VARCHAR(32);

UPDATE company
SET company_code = CONCAT('CV', LPAD(id, 6, '0'))
WHERE company_code IS NULL;

ALTER TABLE company
    MODIFY COLUMN company_code VARCHAR(32) NOT NULL;

ALTER TABLE company
    ADD CONSTRAINT uk_company_company_code UNIQUE (company_code);
