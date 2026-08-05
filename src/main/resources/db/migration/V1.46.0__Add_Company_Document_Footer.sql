-- 공문 발신부(문서 하단 시행/접수·주소·연락처 줄)에 찍히는 기관 정보.
-- 주소는 company.address_name, 홈페이지는 company.homepage_url을 그대로 쓰고
-- 여기서는 그 둘로 덮이지 않는 값만 추가한다.
ALTER TABLE company
    ADD COLUMN postal_code     VARCHAR(10)  NULL COMMENT '우편번호 (공문 발신부 "우" 뒤)',
    ADD COLUMN phone_number    VARCHAR(30)  NULL COMMENT '대표 전화',
    ADD COLUMN fax_number      VARCHAR(30)  NULL COMMENT '팩스 (공문 발신부 "전송")',
    ADD COLUMN contact_email   VARCHAR(255) NULL COMMENT '공문 담당자 E-MAIL',
    ADD COLUMN disclosure_type VARCHAR(20)  NULL COMMENT '공개 구분 (공개/부분공개/비공개). 미설정이면 화면에서 "공개"로 표시';
