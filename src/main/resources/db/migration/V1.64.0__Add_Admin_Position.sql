-- 관리자(app_user)에도 직책을 둔다.
--
-- 지금까지 직책은 직원(members)에만 있어서, 관리자는 결재선 후보와 채팅 참가자 목록에
-- 코드에 박아둔 '관리자'로만 표시됐다. 실제로는 시설장·사무국장인 경우가 많아
-- 결재선을 볼 때 누가 누구인지 알기 어려웠다.
--
-- 직원과 같은 방식으로 둔다 — 표시용 이름(position)과 직책 FK(position_id)를 함께 갖고,
-- 직책 이름이 바뀌어도 과거 표기가 남지 않도록 조회 시 FK를 우선한다.

ALTER TABLE app_user ADD COLUMN position VARCHAR(100) NULL COMMENT '직책 표시명' AFTER username;
ALTER TABLE app_user ADD COLUMN position_id BIGINT NULL COMMENT '직책 FK' AFTER position;

ALTER TABLE app_user ADD CONSTRAINT fk_app_user_position
    FOREIGN KEY (position_id) REFERENCES positions(id) ON DELETE SET NULL;

ALTER TABLE app_user ADD INDEX idx_app_user_position_id (position_id);
