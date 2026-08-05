-- 기관 홈페이지·블로그 주소. 등록하면 관리자/직원 사이드바에 바로가기가 뜬다.
ALTER TABLE company ADD COLUMN homepage_url VARCHAR(500) NULL COMMENT '기관 홈페이지/블로그 주소';
