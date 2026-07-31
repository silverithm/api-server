-- V1.33.0: members.username / members.email 인덱스 복구
--
-- V1.13.0에서 다중 기관 지원을 위해 unique 제약을 제거하면서 인덱스까지 함께 사라졌다.
-- 그 결과 findByUsername/findByEmail(로그인, 서명 조회, 기관 범위 검증)이 매번 풀스캔이었다.
-- 유일성은 더 이상 요구하지 않으므로 non-unique 인덱스로 조회 성능만 복구한다.

SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE()
     AND TABLE_NAME = 'members'
     AND INDEX_NAME = 'idx_members_username') = 0,
    'ALTER TABLE members ADD INDEX idx_members_username (username)',
    'SELECT 1'
));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE()
     AND TABLE_NAME = 'members'
     AND INDEX_NAME = 'idx_members_email') = 0,
    'ALTER TABLE members ADD INDEX idx_members_email (email)',
    'SELECT 1'
));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
