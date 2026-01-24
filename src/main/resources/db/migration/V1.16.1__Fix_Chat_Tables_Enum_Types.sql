-- chat_messages.type을 ENUM으로 변경
ALTER TABLE chat_messages
    MODIFY COLUMN type ENUM('TEXT', 'IMAGE', 'FILE', 'SYSTEM') NOT NULL DEFAULT 'TEXT';

-- chat_rooms.status를 ENUM으로 변경
ALTER TABLE chat_rooms
    MODIFY COLUMN status ENUM('ACTIVE', 'ARCHIVED', 'DELETED') NOT NULL DEFAULT 'ACTIVE';

-- chat_participants.role을 ENUM으로 변경
ALTER TABLE chat_participants
    MODIFY COLUMN role ENUM('ADMIN', 'MEMBER') NOT NULL DEFAULT 'MEMBER';

-- chat_participants.leave_reason을 ENUM으로 변경
ALTER TABLE chat_participants
    MODIFY COLUMN leave_reason ENUM('SELF_LEFT', 'KICKED', 'ACCOUNT_DELETED') NULL;
