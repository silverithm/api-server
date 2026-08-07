-- 회원(직원) 프로필 사진 URL 저장 컬럼 추가.
-- signature_url과 동일한 방식(S3 저장 경로/URL)으로 사용한다.
ALTER TABLE members
    ADD COLUMN profile_image_url VARCHAR(500) NULL;
