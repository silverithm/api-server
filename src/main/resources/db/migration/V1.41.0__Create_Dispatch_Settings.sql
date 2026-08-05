-- 배차 설정(노선·주부운전자·어르신 탑승 순서) 서버 저장
--
-- 그동안 관리자 브라우저 localStorage(zustand persist)에만 있었다. 그래서
-- 다른 PC로 로그인하면 설정이 비어 보이고, 직원 앱은 주·부운전자가 누구인지
-- 알 수 없어 휴무 신청 시 배차 충돌을 막을 수 없었다.
--
-- 프론트가 { routes, seniors } 한 덩어리로 읽고 쓰는 구조라 JSON으로 보관한다.
-- 회사당 한 벌.

CREATE TABLE dispatch_settings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    -- { "routes": [...], "seniors": [...] }
    settings_json LONGTEXT NOT NULL,
    created_at TIMESTAMP,
    modified_at TIMESTAMP,
    UNIQUE KEY uk_dispatch_settings_company (company_id)
);
