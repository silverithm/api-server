-- 기관 홈페이지를 여러 개 등록 (블로그·밴드 등을 함께 운영하는 곳이 많다)
-- 기존 homepage_url은 대표 주소로 그대로 두고(공문 발신부 등이 참조), 목록을 JSON으로 덧붙인다.
ALTER TABLE companies ADD COLUMN homepage_links JSON NULL;
