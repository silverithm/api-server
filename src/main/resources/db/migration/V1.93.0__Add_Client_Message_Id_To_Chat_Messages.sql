-- 보내는 쪽이 붙인 메시지 식별자. 같은 메시지를 다시 보내도 한 건만 남게 하는 열쇠다.
--
-- 앱은 소켓으로 보낸 뒤 서버 응답을 못 받으면 REST로 같은 메시지를 다시 보낸다.
-- 이 열쇠가 없으면 그 재전송이 그대로 두 번째 메시지가 된다.
-- 구버전 앱·웹은 이 값을 안 보내므로 NULL이고, NULL은 유니크 검사에서 빠진다.
ALTER TABLE chat_messages
    ADD COLUMN client_message_id VARCHAR(64) NULL AFTER sender_name;

CREATE UNIQUE INDEX uk_chat_message_client_id
    ON chat_messages (chat_room_id, sender_id, client_message_id);
