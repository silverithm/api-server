-- 결재자가 승인하면서 남기는 의견 (2026-10-08 버그제보방 요청: "중간관리자가 승인할 때도 코멘트를 적을 수 있게").
-- 반려 사유(reject_reason)와 같은 길이. 비어 있으면 의견 없이 승인한 것.
ALTER TABLE approval_steps ADD COLUMN approver_comment VARCHAR(1000) NULL;
