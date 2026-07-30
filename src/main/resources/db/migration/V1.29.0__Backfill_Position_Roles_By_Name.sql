-- V1.27.0 보완.
-- V1.27.0은 user_id가 숫자면 members.id라고 보고 백필했는데, 운영 데이터의 숫자 user_id는
-- members.id와 맞지 않아 대부분 매칭되지 않았다. 이름 기준 백필도 user_id가 숫자가 아닌
-- 행으로만 제한돼 있어서 legacy 역할이 남았다.
-- 여기서는 user_id 형태와 무관하게, 회사 내에서 이름이 유일한 회원에 한해 역할을 맞춘다.
-- (이미 올바른 역할이 들어간 행은 조건에서 걸러져 갱신되지 않는다.)
UPDATE vacation_requests vr
JOIN (
    SELECT company_id,
           name,
           MAX(position) AS position
    FROM members
    WHERE company_id IS NOT NULL
    GROUP BY company_id, name
    HAVING COUNT(*) = 1
       AND MAX(position) IS NOT NULL
       AND MAX(position) <> ''
) unique_member
  ON unique_member.company_id = vr.company_id
 AND unique_member.name = vr.user_name
SET vr.role = unique_member.position
WHERE vr.role <> unique_member.position;

-- 회원 목록에 없는(퇴사·삭제) 신청자의 역할은 그대로 둔다.
-- 조회 시 caregiver/office로 정규화되어 기존과 동일하게 표시된다.
