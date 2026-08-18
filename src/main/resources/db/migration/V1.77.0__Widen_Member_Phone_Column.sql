-- 회원 전화번호 암호화의 후속 — 운영 컬럼이 varchar(20)이라 암호문(~120자)이 들어가지 못했다.
-- (V1.76 배포에서 백필이 여기 걸려 blue 기동이 실패했었다. 데이터는 트랜잭션 롤백으로 무손상.)
ALTER TABLE members MODIFY COLUMN phone_number VARCHAR(512);
