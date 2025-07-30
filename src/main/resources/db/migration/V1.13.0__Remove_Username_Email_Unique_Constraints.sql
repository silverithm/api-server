-- Members 및 MemberJoinRequest 테이블에서 username, email 유니크 제약조건 제거

-- 1. members 테이블의 유니크 제약조건 제거
ALTER TABLE members DROP INDEX username;
ALTER TABLE members DROP INDEX email;

-- 2. member_join_requests 테이블의 유니크 제약조건 제거  
ALTER TABLE member_join_requests DROP INDEX username;
ALTER TABLE member_join_requests DROP INDEX email;