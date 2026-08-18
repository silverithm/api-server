-- 참조 칼럼이 NULL로 저장된 채팅 행을 문자열 식별자에서 다시 채운다.
--
-- V1.68이 채팅 표마다 타입별 참조 칼럼(member_id / app_user_id)을 두고 조회를 그쪽으로
-- 옮겼는데, 엔티티의 @PrePersist에서 참조를 채우는 호출이 if 블록 안에 잘못 들어가
-- (isActive/isDeleted는 @Builder.Default로 항상 값이 있어 그 블록이 돌지 않는다)
-- 그 배포 이후 만들어진 참가자·메시지 행의 참조가 전부 NULL로 저장됐다.
--
-- 결과: 새로 만든 방이 목록 조회(참조 칼럼 기준)에 안 잡혀 "방을 만들었는데 리스트에 없다",
-- 읽음 처리마다 "참가자를 찾을 수 없습니다", 내 메시지가 남의 것처럼 보이는 실제 장애.
--
-- 코드는 같이 고쳤고, 이 마이그레이션은 이미 NULL로 들어간 행을 되살린다.
-- 파생 규칙은 ChatPersonRef와 같다: 'admin_<n>' → app_user_id, 숫자 → member_id,
-- 그 외('system' 등 사람이 아닌 값) → 둘 다 NULL 그대로.
-- 참조 칼럼에는 FK가 걸려 있으므로(V1.68) 실제로 존재하는 계정만 채운다 —
-- 하드 삭제된 직원의 옛 행에 없는 id를 넣으면 여기서 전체가 실패한다.

UPDATE chat_participants p
    JOIN app_user u ON u.id = CAST(SUBSTRING(p.user_id, 7) AS UNSIGNED)
SET p.app_user_id = u.id
WHERE p.app_user_id IS NULL AND p.member_id IS NULL
  AND p.user_id LIKE 'admin\_%' AND SUBSTRING(p.user_id, 7) REGEXP '^[0-9]+$';

UPDATE chat_participants p
    JOIN members m ON m.id = CAST(p.user_id AS UNSIGNED)
SET p.member_id = m.id
WHERE p.app_user_id IS NULL AND p.member_id IS NULL
  AND p.user_id REGEXP '^[0-9]+$';

UPDATE chat_messages msg
    JOIN app_user u ON u.id = CAST(SUBSTRING(msg.sender_id, 7) AS UNSIGNED)
SET msg.sender_app_user_id = u.id
WHERE msg.sender_app_user_id IS NULL AND msg.sender_member_id IS NULL
  AND msg.sender_id LIKE 'admin\_%' AND SUBSTRING(msg.sender_id, 7) REGEXP '^[0-9]+$';

UPDATE chat_messages msg
    JOIN members m ON m.id = CAST(msg.sender_id AS UNSIGNED)
SET msg.sender_member_id = m.id
WHERE msg.sender_app_user_id IS NULL AND msg.sender_member_id IS NULL
  AND msg.sender_id REGEXP '^[0-9]+$';

-- 읽음·리액션도 같은 패턴의 표라 함께 메운다 (버그가 그쪽 @PrePersist에는 없었지만,
-- V1.68 백필 이전에 쌓였거나 다른 경로로 비어 있는 행이 있으면 같은 증상이 난다)
UPDATE chat_message_reads rd
    JOIN app_user u ON u.id = CAST(SUBSTRING(rd.user_id, 7) AS UNSIGNED)
SET rd.app_user_id = u.id
WHERE rd.app_user_id IS NULL AND rd.member_id IS NULL
  AND rd.user_id LIKE 'admin\_%' AND SUBSTRING(rd.user_id, 7) REGEXP '^[0-9]+$';

UPDATE chat_message_reads rd
    JOIN members m ON m.id = CAST(rd.user_id AS UNSIGNED)
SET rd.member_id = m.id
WHERE rd.app_user_id IS NULL AND rd.member_id IS NULL
  AND rd.user_id REGEXP '^[0-9]+$';

UPDATE chat_message_reactions rc
    JOIN app_user u ON u.id = CAST(SUBSTRING(rc.user_id, 7) AS UNSIGNED)
SET rc.app_user_id = u.id
WHERE rc.app_user_id IS NULL AND rc.member_id IS NULL
  AND rc.user_id LIKE 'admin\_%' AND SUBSTRING(rc.user_id, 7) REGEXP '^[0-9]+$';

UPDATE chat_message_reactions rc
    JOIN members m ON m.id = CAST(rc.user_id AS UNSIGNED)
SET rc.member_id = m.id
WHERE rc.app_user_id IS NULL AND rc.member_id IS NULL
  AND rc.user_id REGEXP '^[0-9]+$';
