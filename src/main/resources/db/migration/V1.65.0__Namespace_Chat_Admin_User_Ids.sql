-- 채팅 사용자 식별자에서 관리자(app_user)와 직원(members)을 갈라놓는다.
--
-- 지금까지 채팅은 사람을 원시 숫자 하나로만 가리켰다. 관리자와 직원은 서로 다른 테이블이라
-- id가 겹치는데, 그러면 관리자 3번과 직원 3번이 같은 사람이 되어
--   - 참가자 이름·프로필이 동명이인도 아닌 남의 것으로 표시되고
--   - (chat_room_id, user_id) 유니크 제약 때문에 둘이 같은 방에 들어갈 수 없고
--   - 서로가 보낸 메시지를 자기 것으로 본다.
-- 결재선이 이미 쓰고 있는 표기를 그대로 가져와 관리자만 'admin_<id>'로 적는다.
-- 직원 식별자는 그대로 두므로 직원용 앱은 영향을 받지 않는다.
--
-- 판별 근거는 강한 것부터 적용하고, 어느 근거로도 관리자라고 단정할 수 없는 행은 건드리지
-- 않는다 — 직원 행을 관리자로 잘못 바꾸면 그 사람이 자기 방에서 통째로 사라진다.
-- (특히 회원은 하드 삭제라 '지워진 직원'의 참가자 행이 남아 있을 수 있고, 그 id가 관리자와
--  겹칠 수 있다. 그래서 'app_user에 같은 id가 있다'만으로는 관리자로 보지 않는다.)

-- ── 1단계: 참가자 ────────────────────────────────────────────────────────────
-- (a) 방을 만든 사람. chat_rooms.created_by_name은 로그인 세션이 보낸 실제 이름이라
--     참가자 행에 잘못 저장된 이름(id가 겹치는 직원의 이름)보다 신뢰할 수 있다.
--     이름이 뒤바뀌어 저장된 행은 이 근거로만 바로잡힌다.
UPDATE chat_participants p
    JOIN chat_rooms r ON r.id = p.chat_room_id
    JOIN app_user u ON u.id = CAST(p.user_id AS UNSIGNED) AND u.username = r.created_by_name
SET p.user_id   = CONCAT('admin_', p.user_id),
    p.user_name = u.username
WHERE p.user_id REGEXP '^[0-9]+$'
  AND p.user_id = r.created_by;

-- (b) 저장된 이름이 관리자와 일치하고, 같은 id·같은 이름의 직원은 없는 경우.
UPDATE chat_participants p
    JOIN app_user u ON u.id = CAST(p.user_id AS UNSIGNED) AND u.username = p.user_name
    LEFT JOIN members m ON m.id = CAST(p.user_id AS UNSIGNED) AND m.name = p.user_name
SET p.user_id = CONCAT('admin_', p.user_id)
WHERE p.user_id REGEXP '^[0-9]+$'
  AND m.id IS NULL;

-- ── 2단계: 메시지·읽음·리액션 ────────────────────────────────────────────────
-- 1단계에서 확정된 참가자 행을 근거로 삼는다. 방 안에 'admin_N' 참가자가 있고 원시 'N'
-- 참가자는 없을 때만 바꾼다 — 둘 다 있으면 그 방의 'N'이 누구인지 단정할 수 없다.
-- (마이그레이션 전에는 유니크 제약 때문에 둘이 한 방에 공존할 수 없었으므로 실제로는
--  이 조건이 걸릴 일이 없지만, 잘못 바꾸느니 남겨두는 편이 낫다.)
-- 이름 스냅샷(sender_name 등)은 손대지 않는다. 그 값들은 보낸 시점에 클라이언트가 보낸
-- 본인 이름이라 이미 맞고, 덮어쓰면 이름이 바뀐 사람의 지난 대화가 소급해 바뀐다.
UPDATE chat_messages msg
    JOIN chat_participants ap
        ON ap.chat_room_id = msg.chat_room_id AND ap.user_id = CONCAT('admin_', msg.sender_id)
    LEFT JOIN chat_participants mp
        ON mp.chat_room_id = msg.chat_room_id AND mp.user_id = msg.sender_id
SET msg.sender_id = ap.user_id
WHERE msg.sender_id REGEXP '^[0-9]+$'
  AND mp.id IS NULL;

UPDATE chat_message_reads rd
    JOIN chat_messages msg ON msg.id = rd.message_id
    JOIN chat_participants ap
        ON ap.chat_room_id = msg.chat_room_id AND ap.user_id = CONCAT('admin_', rd.user_id)
    LEFT JOIN chat_participants mp
        ON mp.chat_room_id = msg.chat_room_id AND mp.user_id = rd.user_id
SET rd.user_id = ap.user_id
WHERE rd.user_id REGEXP '^[0-9]+$'
  AND mp.id IS NULL;

UPDATE chat_message_reactions rc
    JOIN chat_messages msg ON msg.id = rc.message_id
    JOIN chat_participants ap
        ON ap.chat_room_id = msg.chat_room_id AND ap.user_id = CONCAT('admin_', rc.user_id)
    LEFT JOIN chat_participants mp
        ON mp.chat_room_id = msg.chat_room_id AND mp.user_id = rc.user_id
SET rc.user_id = ap.user_id
WHERE rc.user_id REGEXP '^[0-9]+$'
  AND mp.id IS NULL;

-- ── 3단계: 방 생성자 ────────────────────────────────────────────────────────
-- created_by는 조회 조건으로 쓰이지 않지만, 원시 id로 남겨두면 다음 사람이 같은 혼동을
-- 반복한다. 1단계에서 관리자로 확정된 방만 맞춰 적는다.
UPDATE chat_rooms r
    JOIN chat_participants p
        ON p.chat_room_id = r.id AND p.user_id = CONCAT('admin_', r.created_by)
SET r.created_by = p.user_id
WHERE r.created_by REGEXP '^[0-9]+$';

-- ── 4단계: 이미 지워진 회원의 참가자 행 정리 ────────────────────────────────
-- 회원 삭제는 하드 삭제인데 지금까지 채팅방에서 내보내는 처리를 하지 않아, 없는 사람이
-- 참가자 목록·참여 인원 수·읽음 집계에 그대로 남아 있다. (앞으로는 MemberService가
-- 삭제할 때 내보낸다.) 계정이 어느 쪽에도 없는 행만 손대므로 산 사람은 영향받지 않는다.
-- 3단계까지 끝난 뒤라 관리자 행은 접두사가 붙어 여기 걸리지 않는다.
UPDATE chat_participants p
    LEFT JOIN members m ON m.id = CAST(p.user_id AS UNSIGNED)
    LEFT JOIN app_user u ON u.id = CAST(p.user_id AS UNSIGNED)
SET p.is_active    = FALSE,
    p.left_at      = NOW(),
    p.leave_reason = 'ACCOUNT_DELETED'
WHERE p.is_active = TRUE
  AND p.user_id REGEXP '^[0-9]+$'
  AND m.id IS NULL
  AND u.id IS NULL;
