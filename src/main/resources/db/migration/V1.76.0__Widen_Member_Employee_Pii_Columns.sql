-- 직원·회원 개인정보 컬럼 암호화(AES-256-GCM, 'v2:' 접두사) 확장.
-- 암호문이 평문보다 길어지는 컬럼만 넓힌다 (전화번호는 기존 255자로 충분).
-- 기존 평문 행은 앱 기동 시 PiiBackfillRunner가 암호문으로 바꾼다.
ALTER TABLE employee MODIFY COLUMN name VARCHAR(512);
ALTER TABLE employee MODIFY COLUMN home_address_name VARCHAR(1024);
ALTER TABLE members MODIFY COLUMN name VARCHAR(512) NOT NULL;
