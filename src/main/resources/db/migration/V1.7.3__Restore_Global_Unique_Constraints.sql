-- V1.7.3: Restore global unique constraints for username and email

-- 회사별 복합 unique 제약조건 제거 (MySQL 문법)
-- members 테이블에서 복합 unique constraint 제거
SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS 
     WHERE TABLE_SCHEMA = DATABASE() 
     AND TABLE_NAME = 'members' 
     AND CONSTRAINT_NAME = 'uk_members_company_username') > 0,
    'ALTER TABLE members DROP INDEX uk_members_company_username',
    'SELECT 1'
));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS 
     WHERE TABLE_SCHEMA = DATABASE() 
     AND TABLE_NAME = 'members' 
     AND CONSTRAINT_NAME = 'uk_members_company_email') > 0,
    'ALTER TABLE members DROP INDEX uk_members_company_email',
    'SELECT 1'
));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- member_join_requests 테이블에서 복합 unique constraint 제거
SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS 
     WHERE TABLE_SCHEMA = DATABASE() 
     AND TABLE_NAME = 'member_join_requests' 
     AND CONSTRAINT_NAME = 'uk_member_join_requests_company_username') > 0,
    'ALTER TABLE member_join_requests DROP INDEX uk_member_join_requests_company_username',
    'SELECT 1'
));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS 
     WHERE TABLE_SCHEMA = DATABASE() 
     AND TABLE_NAME = 'member_join_requests' 
     AND CONSTRAINT_NAME = 'uk_member_join_requests_company_email') > 0,
    'ALTER TABLE member_join_requests DROP INDEX uk_member_join_requests_company_email',
    'SELECT 1'
));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 전역 unique 제약조건 복원
-- members 테이블에 unique constraint 추가 (이미 존재하지 않는 경우만)
SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS 
     WHERE TABLE_SCHEMA = DATABASE() 
     AND TABLE_NAME = 'members' 
     AND CONSTRAINT_NAME = 'username') = 0,
    'ALTER TABLE members ADD CONSTRAINT username UNIQUE (username)',
    'SELECT 1'
));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS 
     WHERE TABLE_SCHEMA = DATABASE() 
     AND TABLE_NAME = 'members' 
     AND CONSTRAINT_NAME = 'email') = 0,
    'ALTER TABLE members ADD CONSTRAINT email UNIQUE (email)',
    'SELECT 1'
));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- member_join_requests 테이블에 unique constraint 추가 (이미 존재하지 않는 경우만)
SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS 
     WHERE TABLE_SCHEMA = DATABASE() 
     AND TABLE_NAME = 'member_join_requests' 
     AND CONSTRAINT_NAME = 'username') = 0,
    'ALTER TABLE member_join_requests ADD CONSTRAINT username UNIQUE (username)',
    'SELECT 1'
));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS 
     WHERE TABLE_SCHEMA = DATABASE() 
     AND TABLE_NAME = 'member_join_requests' 
     AND CONSTRAINT_NAME = 'email') = 0,
    'ALTER TABLE member_join_requests ADD CONSTRAINT email UNIQUE (email)',
    'SELECT 1'
));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt; 