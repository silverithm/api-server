-- V1.2.0__Create_Company_Table.sql
-- Company 테이블 생성


CREATE TABLE company
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    name         VARCHAR(255),
    address_name VARCHAR(255),
    company_latitude DOUBLE,
    company_longitude DOUBLE,
    created_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    modified_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- app_user 테이블에 company_id 컬럼 추가
ALTER TABLE app_user
    ADD COLUMN company_id BIGINT;

-- 외래 키 제약 조건 추가
ALTER TABLE app_user
    ADD CONSTRAINT fk_app_user_company
        FOREIGN KEY (company_id) REFERENCES company (id);