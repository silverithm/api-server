-- 이미 회원이 된 사람의 '대기 중' 가입 신청을 닫는다.
--
-- 같은 사람이 두 번 신청하면 관리자가 그중 하나만 승인하게 되고, 나머지 한 건은 계속
-- 대기 목록에 남는다. 그 사람은 이미 가입됐는데 목록에는 그대로 보이니, 관리자는 승인이
-- 안 된 줄 알고 다시 누른다(실제 문의가 있었다).
--
-- 앞으로는 MemberService.approveJoinRequest가 승인 시 같은 사람의 남은 신청도 함께 닫는다.
-- 이 마이그레이션은 그 처리가 생기기 전에 이미 쌓인 행을 정리한다.
--
-- 대상은 '같은 기관에 같은 이메일 또는 같은 아이디로 이미 등록된 회원이 있는 대기 신청'
-- 뿐이다. 회원이 없는 신청(진짜 대기 중)은 건드리지 않는다.
-- 적용 시점 기준 대상은 1건이었다.

UPDATE member_join_requests jr
    JOIN members m
        ON m.company_id = jr.company_id
       AND (m.email = jr.email OR m.username = jr.username)
SET jr.status = 'APPROVED',
    jr.processed_at = NOW()
WHERE jr.status = 'PENDING';
