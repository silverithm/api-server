-- V1.19.0: 출석 관리 테이블 생성

-- 직원 출퇴근 테이블
CREATE TABLE employee_attendance (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_id BIGINT NOT NULL,
    company_id BIGINT NOT NULL,
    date DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ABSENT',
    check_in_time TIME,
    check_out_time TIME,
    note VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    modified_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_emp_att_member FOREIGN KEY (member_id) REFERENCES members(id),
    CONSTRAINT fk_emp_att_company FOREIGN KEY (company_id) REFERENCES company(id),
    UNIQUE KEY uk_emp_att_member_date (member_id, date)
);

CREATE INDEX idx_emp_att_company_date ON employee_attendance(company_id, date);
CREATE INDEX idx_emp_att_company_date_status ON employee_attendance(company_id, date, status);

-- 어르신 등원 테이블
CREATE TABLE elder_attendance (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    elderly_id BIGINT NOT NULL,
    company_id BIGINT NOT NULL,
    date DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ABSENT',
    note VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    modified_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_eld_att_elderly FOREIGN KEY (elderly_id) REFERENCES elderly(node_id),
    CONSTRAINT fk_eld_att_company FOREIGN KEY (company_id) REFERENCES company(id),
    UNIQUE KEY uk_eld_att_elderly_date (elderly_id, date)
);

CREATE INDEX idx_eld_att_company_date ON elder_attendance(company_id, date);
CREATE INDEX idx_eld_att_company_date_status ON elder_attendance(company_id, date, status);
