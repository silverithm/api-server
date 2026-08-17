-- 방 공지에 첨부 파일 참조를 함께 저장한다.
-- 승인된 공문을 공지로 올릴 때 요약 텍스트를 고정하면서도, 공지 배너에서
-- 공문 파일(PDF 등)을 바로 열 수 있게 파일명·URL을 스냅샷으로 남긴다.
-- (notice_content와 같은 원리 — 원본 메시지가 삭제돼도 공지의 파일 링크는 남는다)
ALTER TABLE chat_rooms
    ADD COLUMN notice_file_name VARCHAR(255) NULL,
    ADD COLUMN notice_file_url VARCHAR(1000) NULL;
