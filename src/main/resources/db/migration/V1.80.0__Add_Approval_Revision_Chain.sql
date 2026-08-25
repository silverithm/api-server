-- 반려된 기안을 고쳐 다시 올릴 때, 원본을 지우거나 덮지 않고 '다음 차수'로 잇는다.
--
-- 같은 행을 되살리지 않는 이유: 반려 사유와 결재자 서명은 approval_step에 붙어 있어서
-- 재상신하며 스텝을 다시 쓰면 그 기록이 사라진다. 무엇이 왜 반려됐는지는 남아야 한다.
ALTER TABLE approval_requests
    ADD COLUMN revised_from_id BIGINT NULL COMMENT '이 기안이 고쳐 올린 원본(반려 건) id',
    ADD COLUMN revision INT NOT NULL DEFAULT 1 COMMENT '차수. 원본이 1, 고쳐 올릴 때마다 +1';

-- 목록에서 '대체된 차수'를 접으려면 원본 id로 찾는 조회가 매번 돈다
CREATE INDEX idx_approval_requests_revised_from ON approval_requests (revised_from_id);

-- 원본이 지워지면 링크만 끊고 고쳐 올린 기안은 남긴다.
-- RESTRICT로 두면 '반려 건 삭제'라는 기존 동작이 갑자기 실패한다 — 새 기능이
-- 원래 되던 일을 막아서는 안 된다. 링크가 끊겨도 revision 값은 남아 차수는 읽힌다.
ALTER TABLE approval_requests
    ADD CONSTRAINT fk_approval_requests_revised_from
    FOREIGN KEY (revised_from_id) REFERENCES approval_requests (id) ON DELETE SET NULL;
