-- V1.2.2__Remove_Old_Company_Columns.sql
-- 마이그레이션이 완료된 후 기존 컬럼 제거
-- 주의: 모든 데이터가 제대로 마이그레이션되었는지 확인 후 실행!

ALTER TABLE app_user DROP COLUMN company_name;
ALTER TABLE app_user DROP COLUMN company_address_name;
ALTER TABLE app_user DROP COLUMN company_latitude;
ALTER TABLE app_user DROP COLUMN company_longitude;