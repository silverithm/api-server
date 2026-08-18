-- 어르신 이름·주소 컬럼 암호화(AES-256-GCM, 'v2:' 접두사) 도입.
-- 암호문이 평문보다 길어지므로 컬럼을 먼저 넓힌다.
-- 기존 평문 행은 앱 기동 시 ElderPiiBackfillRunner가 암호문으로 바꾼다.
ALTER TABLE elderly MODIFY COLUMN name VARCHAR(512);
ALTER TABLE elderly MODIFY COLUMN home_address_name VARCHAR(1024);
