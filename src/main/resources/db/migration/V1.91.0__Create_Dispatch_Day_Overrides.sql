-- V1.91.0: 그날 하루치 배차 수정본
--
-- 배차표는 노선 설정(주·부운전자, 어르신 탑승 순서)에서 매일 다시 계산된다. 그런데 현장에서는
-- "오늘은 저 어르신을 저 차에 태운다" 같은 그날만의 조정이 늘 생긴다. 지금까지는 그걸 담을 자리가
-- 없어 노선 설정 자체를 고쳐야 했고, 그러면 다음 날부터도 바뀌어 버렸다.
--
-- 그래서 하루치 수정본을 따로 둔다. 설정은 그대로 두고 그날 화면에만 얹는다.
-- 수정본이 없는 날은 예전과 똑같이 설정대로 계산된다.

CREATE TABLE dispatch_day_overrides (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    dispatch_date DATE NOT NULL,
    -- [{ "seniorId": "...", "routeId": "...", "tripOrder": 1, "boardingOrder": 3 }, ...]
    overrides_json LONGTEXT NOT NULL,
    created_at DATETIME NULL,
    modified_at DATETIME NULL,
    CONSTRAINT uk_dispatch_day_overrides UNIQUE (company_id, dispatch_date)
);
