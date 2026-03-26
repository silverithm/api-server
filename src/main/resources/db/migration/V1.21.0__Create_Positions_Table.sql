-- 직책 관리 테이블
CREATE TABLE IF NOT EXISTS positions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL COMMENT '직책명',
    description VARCHAR(255) COMMENT '설명',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '정렬순서',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (company_id) REFERENCES company(id) ON DELETE CASCADE,
    UNIQUE KEY uk_position_company_name (company_id, name),
    INDEX idx_positions_company (company_id),
    INDEX idx_positions_sort (company_id, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='직책 관리 테이블';

-- members 테이블에 position_id FK 추가
ALTER TABLE members ADD COLUMN position_id BIGINT NULL COMMENT '직책 FK' AFTER position;
ALTER TABLE members ADD CONSTRAINT fk_members_position FOREIGN KEY (position_id) REFERENCES positions(id) ON DELETE SET NULL;
ALTER TABLE members ADD INDEX idx_members_position_id (position_id);

-- chat_messages 테이블에 sender_position 추가
ALTER TABLE chat_messages ADD COLUMN sender_position VARCHAR(100) NULL COMMENT '발신자 직책' AFTER sender_name;

-- 기존 free-text position 데이터가 있는 경우 positions 테이블로 마이그레이션
INSERT IGNORE INTO positions (company_id, name, sort_order)
SELECT DISTINCT m.company_id, m.position, 0
FROM members m
WHERE m.position IS NOT NULL AND m.position != '' AND m.company_id IS NOT NULL;

-- members의 position_id 업데이트
UPDATE members m
JOIN positions p ON m.company_id = p.company_id AND m.position = p.name
SET m.position_id = p.id
WHERE m.position IS NOT NULL AND m.position != '';
