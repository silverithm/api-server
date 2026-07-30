-- 기존 CAREGIVER/OFFICE 분류만 쓰던 회사를 역할(Position) 체계로 이관한다.
-- 1) 회사별 기본 역할 시딩  2) 미배정 회원 백필  3) 기존 휴가 신청의 역할 백필

-- 1) 아직 역할이 배정되지 않은 CAREGIVER/OFFICE 회원이 있는 회사에 기본 역할 생성
INSERT IGNORE INTO positions (company_id, name, description, member_role, sort_order, created_at, updated_at)
SELECT DISTINCT m.company_id, '요양보호사', '기존 요양보호사 분류에서 자동 생성', 'CAREGIVER', 0, NOW(), NOW()
FROM members m
WHERE m.role = 'CAREGIVER'
  AND m.company_id IS NOT NULL
  AND (m.position IS NULL OR m.position = '');

INSERT IGNORE INTO positions (company_id, name, description, member_role, sort_order, created_at, updated_at)
SELECT DISTINCT m.company_id, '사무직', '기존 사무직 분류에서 자동 생성', 'OFFICE', 1, NOW(), NOW()
FROM members m
WHERE m.role = 'OFFICE'
  AND m.company_id IS NOT NULL
  AND (m.position IS NULL OR m.position = '');

-- 2) 역할이 비어 있는 회원을 기존 분류에 맞는 역할로 배정 (관리자/일반 사용자는 제외)
UPDATE members m
JOIN positions p ON p.company_id = m.company_id AND p.name = '요양보호사'
SET m.position = p.name,
    m.position_id = p.id
WHERE m.role = 'CAREGIVER'
  AND (m.position IS NULL OR m.position = '');

UPDATE members m
JOIN positions p ON p.company_id = m.company_id AND p.name = '사무직'
SET m.position = p.name,
    m.position_id = p.id
WHERE m.role = 'OFFICE'
  AND (m.position IS NULL OR m.position = '');

-- 3-1) 휴가 신청의 역할을 회원에게 배정된 역할로 맞춘다 (user_id가 members.id인 경우)
UPDATE vacation_requests vr
JOIN members m
  ON m.company_id = vr.company_id
 AND m.id = CAST(vr.user_id AS UNSIGNED)
SET vr.role = m.position
WHERE vr.user_id REGEXP '^[0-9]+$'
  AND m.position IS NOT NULL
  AND m.position <> ''
  AND vr.role <> m.position;

-- 3-2) user_id로 못 찾는 예전 데이터는 회사 내에서 이름이 유일할 때만 이름으로 맞춘다
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
WHERE (vr.user_id IS NULL OR vr.user_id NOT REGEXP '^[0-9]+$')
  AND vr.role <> unique_member.position;

-- vacation_limits.role은 그대로 둔다.
-- 애플리케이션이 '요양보호사'/'사무직'을 legacy caregiver/office와 동일하게 정규화하므로
-- 시딩된 기본 역할과 기존 상한 설정이 그대로 이어진다.
