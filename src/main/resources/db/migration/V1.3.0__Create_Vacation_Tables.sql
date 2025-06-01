-- 휴가 신청 테이블
CREATE TABLE vacation_requests (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_name VARCHAR(100) NOT NULL COMMENT '사용자 이름',
    date DATE NOT NULL COMMENT '휴가 날짜',
    status ENUM('PENDING', 'APPROVED', 'REJECTED') NOT NULL DEFAULT 'PENDING' COMMENT '승인 상태',
    role ENUM('CAREGIVER', 'OFFICE', 'ALL') NOT NULL DEFAULT 'CAREGIVER' COMMENT '직원 역할',
    reason VARCHAR(500) COMMENT '휴가 사유',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',
    
    INDEX idx_date (date),
    INDEX idx_user_name (user_name),
    INDEX idx_role (role),
    INDEX idx_status (status),
    INDEX idx_date_role (date, role)
) COMMENT='휴가 신청 테이블';

-- 휴가 제한 테이블
CREATE TABLE vacation_limits (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    date DATE NOT NULL COMMENT '제한 적용 날짜',
    max_people INT NOT NULL DEFAULT 3 COMMENT '최대 휴가 인원',
    role ENUM('CAREGIVER', 'OFFICE', 'ALL') NOT NULL DEFAULT 'CAREGIVER' COMMENT '직원 역할',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',
    
    UNIQUE KEY uk_date_role (date, role),
    INDEX idx_date (date),
    INDEX idx_role (role)
) COMMENT='휴가 인원 제한 테이블'; 