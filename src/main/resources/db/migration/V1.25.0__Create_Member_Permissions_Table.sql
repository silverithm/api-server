-- 멤버별 세부 권한 테이블
CREATE TABLE member_permissions (
    member_id BIGINT NOT NULL,
    permission VARCHAR(50) NOT NULL,
    PRIMARY KEY (member_id, permission),
    CONSTRAINT fk_member_permissions_member FOREIGN KEY (member_id) REFERENCES members(id) ON DELETE CASCADE
);

CREATE INDEX idx_member_permissions_member_id ON member_permissions(member_id);
