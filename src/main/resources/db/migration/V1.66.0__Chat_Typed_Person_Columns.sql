-- 채팅이 가리키는 '사람'을 문자열이 아니라 제대로 된 참조로 바꾸기 (1단계: 칼럼 추가·백필)
--
-- 지금 채팅은 사람을 user_id VARCHAR 하나로 가리키고, 거기에 직원은 "12", 관리자는 "admin_12"가
-- 섞여 들어간다. 다형 외래키를 문자열로 인코딩한 형태라 DB가 무결성을 지켜주지 못한다.
-- 실제로 이 시점 운영에는 대응하는 직원이 없는 참가자 행 69개, 메시지 58개가 남아 있고,
-- 그중 62개는 숫자가 관리자 id와도 겹쳐 누구인지 단정할 수 없다.
--
-- 그래서 표마다 member_id / app_user_id 두 칼럼을 두고 둘 중 하나만 채운다(배타적 참조).
-- 이 단계에서는 기존 문자열 칼럼을 그대로 두고 새 칼럼을 채우기만 한다 —
-- 애플리케이션은 아직 문자열로 조회하므로 배포해도 동작이 바뀌지 않는다.
--
-- 삭제 정책은 표마다 다르게 둔다.
--   참가자·읽음·리액션: 사람이 사라지면 함께 지운다 (방에 남아 있을 이유가 없다)
--   메시지·방 생성자: 사람이 사라져도 대화 기록은 남아야 하므로 NULL로 풀고 이름 스냅샷을 쓴다
-- 짝이 안 맞는 옛 행(위의 69개 등)은 두 칼럼이 모두 NULL로 남는다. 그래서 "정확히 하나"가
-- 아니라 "둘 다 채워지지는 않는다"만 강제한다.

-- ── chat_participants ────────────────────────────────────────────────────────
ALTER TABLE chat_participants
    ADD COLUMN member_id BIGINT NULL COMMENT '직원 참조' AFTER user_id,
    ADD COLUMN app_user_id BIGINT NULL COMMENT '관리자 계정 참조' AFTER member_id;

UPDATE chat_participants SET member_id = CAST(user_id AS UNSIGNED)
 WHERE user_id REGEXP '^[0-9]+$'
   AND EXISTS (SELECT 1 FROM members m WHERE m.id = CAST(chat_participants.user_id AS UNSIGNED));

UPDATE chat_participants SET app_user_id = CAST(SUBSTRING(user_id, 7) AS UNSIGNED)
 WHERE user_id LIKE 'admin\_%'
   AND EXISTS (SELECT 1 FROM app_user u WHERE u.id = CAST(SUBSTRING(chat_participants.user_id, 7) AS UNSIGNED));

ALTER TABLE chat_participants
    ADD CONSTRAINT fk_chat_participants_member FOREIGN KEY (member_id) REFERENCES members (id) ON DELETE CASCADE,
    ADD CONSTRAINT fk_chat_participants_app_user FOREIGN KEY (app_user_id) REFERENCES app_user (id) ON DELETE CASCADE,
    ADD CONSTRAINT ck_chat_participants_person CHECK (member_id IS NULL OR app_user_id IS NULL);
CREATE INDEX idx_chat_participants_member ON chat_participants (member_id);
CREATE INDEX idx_chat_participants_app_user ON chat_participants (app_user_id);

-- ── chat_messages ────────────────────────────────────────────────────────────
ALTER TABLE chat_messages
    ADD COLUMN sender_member_id BIGINT NULL COMMENT '보낸 직원' AFTER sender_id,
    ADD COLUMN sender_app_user_id BIGINT NULL COMMENT '보낸 관리자 계정' AFTER sender_member_id;

UPDATE chat_messages SET sender_member_id = CAST(sender_id AS UNSIGNED)
 WHERE sender_id REGEXP '^[0-9]+$'
   AND EXISTS (SELECT 1 FROM members m WHERE m.id = CAST(chat_messages.sender_id AS UNSIGNED));

UPDATE chat_messages SET sender_app_user_id = CAST(SUBSTRING(sender_id, 7) AS UNSIGNED)
 WHERE sender_id LIKE 'admin\_%'
   AND EXISTS (SELECT 1 FROM app_user u WHERE u.id = CAST(SUBSTRING(chat_messages.sender_id, 7) AS UNSIGNED));

ALTER TABLE chat_messages
    ADD CONSTRAINT fk_chat_messages_member FOREIGN KEY (sender_member_id) REFERENCES members (id) ON DELETE SET NULL,
    ADD CONSTRAINT fk_chat_messages_app_user FOREIGN KEY (sender_app_user_id) REFERENCES app_user (id) ON DELETE SET NULL;
-- 여기는 CHECK를 걸지 못한다: MySQL은 ON DELETE SET NULL 대상 칼럼을 CHECK에 쓰지 못하게 막는다
-- (사람이 지워질 때 NULL로 푸는 동작과 CHECK가 부딪힐 수 있어서다).
-- 대화 기록은 남겨야 하므로 SET NULL을 택하고, '둘 다 채우지 않기'는 코드에서 지킨다.
CREATE INDEX idx_chat_messages_member ON chat_messages (sender_member_id);
CREATE INDEX idx_chat_messages_app_user ON chat_messages (sender_app_user_id);

-- ── chat_message_reads ───────────────────────────────────────────────────────
ALTER TABLE chat_message_reads
    ADD COLUMN member_id BIGINT NULL AFTER user_id,
    ADD COLUMN app_user_id BIGINT NULL AFTER member_id;

UPDATE chat_message_reads SET member_id = CAST(user_id AS UNSIGNED)
 WHERE user_id REGEXP '^[0-9]+$'
   AND EXISTS (SELECT 1 FROM members m WHERE m.id = CAST(chat_message_reads.user_id AS UNSIGNED));

UPDATE chat_message_reads SET app_user_id = CAST(SUBSTRING(user_id, 7) AS UNSIGNED)
 WHERE user_id LIKE 'admin\_%'
   AND EXISTS (SELECT 1 FROM app_user u WHERE u.id = CAST(SUBSTRING(chat_message_reads.user_id, 7) AS UNSIGNED));

ALTER TABLE chat_message_reads
    ADD CONSTRAINT fk_chat_reads_member FOREIGN KEY (member_id) REFERENCES members (id) ON DELETE CASCADE,
    ADD CONSTRAINT fk_chat_reads_app_user FOREIGN KEY (app_user_id) REFERENCES app_user (id) ON DELETE CASCADE,
    ADD CONSTRAINT ck_chat_reads_person CHECK (member_id IS NULL OR app_user_id IS NULL);
CREATE INDEX idx_chat_reads_member ON chat_message_reads (member_id);
CREATE INDEX idx_chat_reads_app_user ON chat_message_reads (app_user_id);

-- ── chat_message_reactions ───────────────────────────────────────────────────
ALTER TABLE chat_message_reactions
    ADD COLUMN member_id BIGINT NULL AFTER user_id,
    ADD COLUMN app_user_id BIGINT NULL AFTER member_id;

UPDATE chat_message_reactions SET member_id = CAST(user_id AS UNSIGNED)
 WHERE user_id REGEXP '^[0-9]+$'
   AND EXISTS (SELECT 1 FROM members m WHERE m.id = CAST(chat_message_reactions.user_id AS UNSIGNED));

UPDATE chat_message_reactions SET app_user_id = CAST(SUBSTRING(user_id, 7) AS UNSIGNED)
 WHERE user_id LIKE 'admin\_%'
   AND EXISTS (SELECT 1 FROM app_user u WHERE u.id = CAST(SUBSTRING(chat_message_reactions.user_id, 7) AS UNSIGNED));

ALTER TABLE chat_message_reactions
    ADD CONSTRAINT fk_chat_reactions_member FOREIGN KEY (member_id) REFERENCES members (id) ON DELETE CASCADE,
    ADD CONSTRAINT fk_chat_reactions_app_user FOREIGN KEY (app_user_id) REFERENCES app_user (id) ON DELETE CASCADE,
    ADD CONSTRAINT ck_chat_reactions_person CHECK (member_id IS NULL OR app_user_id IS NULL);

-- ── chat_rooms (만든 사람) ───────────────────────────────────────────────────
ALTER TABLE chat_rooms
    ADD COLUMN creator_member_id BIGINT NULL AFTER created_by,
    ADD COLUMN creator_app_user_id BIGINT NULL AFTER creator_member_id;

UPDATE chat_rooms SET creator_member_id = CAST(created_by AS UNSIGNED)
 WHERE created_by REGEXP '^[0-9]+$'
   AND EXISTS (SELECT 1 FROM members m WHERE m.id = CAST(chat_rooms.created_by AS UNSIGNED));

UPDATE chat_rooms SET creator_app_user_id = CAST(SUBSTRING(created_by, 7) AS UNSIGNED)
 WHERE created_by LIKE 'admin\_%'
   AND EXISTS (SELECT 1 FROM app_user u WHERE u.id = CAST(SUBSTRING(chat_rooms.created_by, 7) AS UNSIGNED));

ALTER TABLE chat_rooms
    ADD CONSTRAINT fk_chat_rooms_creator_member FOREIGN KEY (creator_member_id) REFERENCES members (id) ON DELETE SET NULL,
    ADD CONSTRAINT fk_chat_rooms_creator_app_user FOREIGN KEY (creator_app_user_id) REFERENCES app_user (id) ON DELETE SET NULL;
-- 위와 같은 이유로 CHECK 없음 (만든 사람이 지워져도 방과 이름은 남아야 한다)
