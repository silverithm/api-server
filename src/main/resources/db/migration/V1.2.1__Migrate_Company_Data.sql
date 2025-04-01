-- V1.2.1__Migrate_Company_Data.sql
-- 기존 사용자 데이터에서 고유한 회사 데이터 추출하여 company 테이블에 삽입

INSERT INTO company (name, address_name, company_latitude, company_longitude, created_at, updated_at)
SELECT DISTINCT
    au.company_name,
    au.company_address_name,
    au.company_latitude,
    au.company_longitude,
    NOW(),
    NOW()
FROM app_user au
WHERE au.company_name IS NOT NULL;

-- 사용자 레코드 업데이트하여 회사 ID 연결
UPDATE app_user a
    JOIN company c ON a.company_name = c.name
    AND ((a.company_address_name = c.address_name) OR (a.company_address_name IS NULL AND c.address_name IS NULL))
    SET a.company_id = c.id;