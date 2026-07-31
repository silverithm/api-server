-- 케어브이 광장 운영자(관리자)
-- 광장은 전 기관 공유 리소스라 기관 관리자(company admin)와는 별개의 권한이다.
-- 여기에 등록된 계정만 [운영] 공지 작성과 타인 글 삭제를 할 수 있다.
-- 운영자 추가/삭제는 이 테이블에 INSERT/DELETE만 하면 되고 재배포는 필요 없다.

CREATE TABLE plaza_admins (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    -- 로그인 이메일 (JWT subject == 이메일)
    email VARCHAR(255) NOT NULL,
    -- 운영 화면에서 누구인지 알아보기 위한 메모용 이름
    display_name VARCHAR(100),
    memo VARCHAR(255),
    created_at TIMESTAMP,
    modified_at TIMESTAMP,
    UNIQUE KEY uk_plaza_admins_email (email)
);

-- 관리자 모드로 작성한 [운영] 공지 여부
ALTER TABLE plaza_posts ADD COLUMN is_official BOOLEAN NOT NULL DEFAULT FALSE;

-- 초기 운영자
INSERT INTO plaza_admins (email, display_name, memo, created_at, modified_at)
VALUES ('ggprgrkjh@naver.com', '김도형', '숲속재활어르신재가복지센터', NOW(), NOW());

-- 컬럼 신설 이전에 올린 운영 공지([운영] 말머리)를 새 플래그에 맞춰준다.
-- 이 글들은 작성자가 '케어브이 운영팀'으로 표시된다.
UPDATE plaza_posts SET is_official = TRUE WHERE title LIKE '[운영]%';
