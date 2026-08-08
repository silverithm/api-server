-- 사용자별 기기 토큰.
--
-- 지금까지는 members.fcm_token / app_user.fcm_token 컬럼 하나에만 담아서, 새 기기에서
-- 앱을 켜면 이전 기기 토큰을 덮어썼다. 폰과 태블릿을 같이 쓰면 마지막에 켠 쪽만 알림을 받고
-- 나머지는 아무 표시 없이 끊겼다. 한 기기에서 로그아웃하면 컬럼이 통째로 비워져 다른 기기까지
-- 같이 멈추는 문제도 있었다.
--
-- 기기를 행으로 두면 로그인한 모든 기기가 알림을 받고, 로그아웃은 그 기기만 해제한다.
CREATE TABLE user_devices (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    -- 직원(members.id) 또는 관리자 가입 계정(app_user.id) 중 하나만 채워진다
    member_id BIGINT NULL,
    app_user_id BIGINT NULL,
    -- 같은 기기가 다른 계정으로 로그인하면 주인이 바뀌어야 하므로 토큰이 유일 키다
    fcm_token VARCHAR(500) NOT NULL,
    platform VARCHAR(20) NULL COMMENT 'ios / android / web',
    created_at TIMESTAMP NULL,
    last_seen_at TIMESTAMP NULL,
    -- 500자 × 4바이트 = 2000바이트로 InnoDB 인덱스 한계(3072) 안이라 전체 길이로 건다.
    -- 앞부분만 잘라 걸면 앞 191자가 같은 서로 다른 토큰이 충돌한다.
    UNIQUE KEY uk_user_devices_token (fcm_token),
    KEY idx_user_devices_member (member_id),
    KEY idx_user_devices_app_user (app_user_id)
);

-- 지금 알림을 받고 있는 사람들이 끊기지 않도록 기존 토큰을 옮긴다.
-- 같은 토큰이 여러 행에 있으면 유일 키에 걸리므로 IGNORE로 건너뛴다
-- (대상 테이블을 서브쿼리로 다시 읽는 방식은 MySQL이 막는 경우가 있어 쓰지 않는다).
INSERT IGNORE INTO user_devices (member_id, fcm_token, created_at, last_seen_at)
SELECT m.id, m.fcm_token, NOW(), NOW()
FROM members m
WHERE m.fcm_token IS NOT NULL AND m.fcm_token <> '';

INSERT IGNORE INTO user_devices (app_user_id, fcm_token, created_at, last_seen_at)
SELECT a.id, a.fcm_token, NOW(), NOW()
FROM app_user a
WHERE a.fcm_token IS NOT NULL AND a.fcm_token <> '';
