-- 감사 로그: 인증된 쓰기 요청(POST/PUT/DELETE/PATCH)의 누가·언제·무엇을 기록
CREATE TABLE audit_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    occurred_at DATETIME NOT NULL COMMENT '요청 처리 시각',
    username VARCHAR(255) NOT NULL COMMENT '요청자 (관리자 email 또는 직원 username)',
    company_id BIGINT NULL COMMENT '요청자 소속 기관 (해석 실패 시 NULL)',
    method VARCHAR(10) NOT NULL,
    uri VARCHAR(500) NOT NULL,
    status_code INT NOT NULL,
    client_ip VARCHAR(64) NULL,
    INDEX idx_audit_company_time (company_id, occurred_at),
    INDEX idx_audit_time (occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
