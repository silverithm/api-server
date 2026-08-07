-- 사용자별 푸시 알림 수신 on/off.
--
-- 기본값 TRUE — 기존 사용자는 지금까지처럼 알림을 받는다. 끈 사람만 FALSE가 된다.
-- 토큰을 지우는 방식이 아니라 별도 플래그로 두는 이유: 껐다 켤 때 토큰을 다시 받아오는
-- 과정(권한·APNS 대기)이 필요 없고, 껐던 사람도 켜는 즉시 바로 받을 수 있다.
ALTER TABLE members ADD COLUMN push_enabled BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE app_user ADD COLUMN push_enabled BOOLEAN NOT NULL DEFAULT TRUE;
