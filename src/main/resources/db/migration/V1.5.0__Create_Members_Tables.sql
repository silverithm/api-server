-- 회원 테이블
CREATE TABLE members (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE COMMENT '사용자명',
    password VARCHAR(255) NOT NULL COMMENT '비밀번호',
    name VARCHAR(100) NOT NULL COMMENT '이름',
    email VARCHAR(255) NOT NULL UNIQUE COMMENT '이메일',
    phone_number VARCHAR(20) COMMENT '전화번호',
    role ENUM('ADMIN', 'CAREGIVER', 'OFFICE', 'USER') NOT NULL DEFAULT 'USER' COMMENT '역할',
    status ENUM('ACTIVE', 'INACTIVE', 'SUSPENDED') NOT NULL DEFAULT 'ACTIVE' COMMENT '상태',
    fcm_token VARCHAR(500) COMMENT 'FCM 토큰',
    department VARCHAR(100) COMMENT '부서',
    position VARCHAR(100) COMMENT '직책',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',
    
    INDEX idx_username (username),
    INDEX idx_email (email),
    INDEX idx_role (role),
    INDEX idx_status (status),
    INDEX idx_department (department),
    INDEX idx_created_at (created_at)
) COMMENT='회원 정보 테이블';

-- 회원가입 요청 테이블
CREATE TABLE member_join_requests (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE COMMENT '요청 사용자명',
    password VARCHAR(255) NOT NULL COMMENT '비밀번호',
    name VARCHAR(100) NOT NULL COMMENT '이름',
    email VARCHAR(255) NOT NULL UNIQUE COMMENT '이메일',
    phone_number VARCHAR(20) COMMENT '전화번호',
    requested_role ENUM('ADMIN', 'CAREGIVER', 'OFFICE', 'USER') NOT NULL DEFAULT 'USER' COMMENT '요청 역할',
    department VARCHAR(100) COMMENT '부서',
    position VARCHAR(100) COMMENT '직책',
    fcm_token VARCHAR(500) COMMENT 'FCM 토큰',
    status ENUM('PENDING', 'APPROVED', 'REJECTED') NOT NULL DEFAULT 'PENDING' COMMENT '처리 상태',
    reject_reason VARCHAR(500) COMMENT '거부 사유',
    approved_by BIGINT COMMENT '승인/거부한 관리자 ID',
    processed_at DATETIME COMMENT '처리 일시',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',
    
    INDEX idx_username (username),
    INDEX idx_email (email),
    INDEX idx_status (status),
    INDEX idx_requested_role (requested_role),
    INDEX idx_created_at (created_at),
    INDEX idx_processed_at (processed_at)
) COMMENT='회원가입 요청 테이블';

-- 알림 테이블에 회원 관련 알림 타입 추가
ALTER TABLE notifications 
MODIFY COLUMN type ENUM(
    'VACATION_APPROVED', 
    'VACATION_REJECTED', 
    'VACATION_SUBMITTED', 
    'VACATION_REMINDER',
    'MEMBER_JOIN_REQUESTED',
    'MEMBER_JOIN_APPROVED', 
    'MEMBER_JOIN_REJECTED',
    'GENERAL'
) NOT NULL DEFAULT 'GENERAL' COMMENT '알림 타입'; 