-- 커뮤니티 구인구직 게시판
--
-- 게시판(board)에 job_offer(구인)·job_seek(구직)이 추가된다. 기존 게시판과
-- 같은 글 구조를 쓰되, 연락처만 별도 컬럼으로 둔다. 연락처를 본문에 적으면
-- 비공개 선택이 불가능하고 크롤링에도 그대로 노출되기 때문이다.

ALTER TABLE plaza_posts
    ADD COLUMN contact_info VARCHAR(200) NULL COMMENT '구인구직 연락처 (전화·이메일 등)',
    ADD COLUMN contact_public BOOLEAN NOT NULL DEFAULT FALSE COMMENT '연락처 전체 공개 여부 (false면 로그인 회원에게만)';
