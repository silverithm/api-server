-- 관리자(app_user)에도 프로필 사진을 둔다.
--
-- 프로필 사진은 직원(members.profile_image_url)에만 있어서, 관리자는 채팅 참가자 목록과
-- 결재선 후보 어디서도 이니셜 아바타로만 보였다. 관리자도 기관 사람이고 같은 목록에
-- 나란히 뜨는데 혼자만 사진이 없으면 누구인지 알아보기 어렵다.
--
-- 컬럼 규격은 직원 쪽과 맞춘다 — 절대 URL을 담으므로 넉넉히 1000자.

ALTER TABLE app_user
    ADD COLUMN profile_image_url VARCHAR(1000) NULL COMMENT '관리자 프로필 사진 URL' AFTER signature_url;
