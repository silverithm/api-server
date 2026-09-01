-- V1.88.0: 일정 담당자에 종류(MEMBER/ADMIN) 컬럼 추가
--
-- members.id와 app_user.id는 서로 다른 시퀀스라 값이 겹칠 수 있다.
-- schedules.manager_member_id만으로는 어느 테이블을 가리키는지 알 수 없어,
-- 관리자(app_user) id를 담당자로 저장하면 엉뚱한 회사의 members 행이 담당자로
-- 조회되는 사고가 있었다(운영 확인). 종류를 함께 저장해 조회 시 올바른 테이블을 고르게 한다.
-- 기존 행은 전부 members 테이블을 가리키던 값이므로 'MEMBER'로 채운다.

ALTER TABLE schedules
    ADD COLUMN manager_type VARCHAR(20) NOT NULL DEFAULT 'MEMBER';
