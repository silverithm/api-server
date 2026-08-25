-- 회의록 → 결재함 문서 연결을 ON DELETE SET NULL로.
--
-- 관리자가 문서함에서 회의록 문서를 지우면 meeting_minutes가 그 문서를 FK로 물고 있어
-- 삭제가 통째로 막혔다 (Cannot delete or update a parent row). 문서를 지우면
-- 회의록은 남고 연결만 풀리는 것이 맞다 — 회의 기록 자체는 회의록 화면에 있다.

ALTER TABLE meeting_minutes
    DROP FOREIGN KEY fk_meeting_minutes_approval;

ALTER TABLE meeting_minutes
    ADD CONSTRAINT fk_meeting_minutes_approval
        FOREIGN KEY (approval_request_id) REFERENCES approval_requests (id)
        ON DELETE SET NULL;
