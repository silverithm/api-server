-- V1.7.7: Member의 MemberStatus enum에 DELETED 상태 추가
-- 회원탈퇴 기능을 위한 상태 추가

-- members 테이블의 status 컬럼에 DELETED 값 허용
-- 기존에 ENUM 타입으로 제한되어 있다면 DELETED 값을 추가
ALTER TABLE members MODIFY COLUMN status ENUM('ACTIVE', 'INACTIVE', 'SUSPENDED', 'DELETED') NOT NULL; 