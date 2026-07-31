-- 케어브이 광장: 게시판(글/댓글/좋아요/조회/신고) + 자료실
-- 전 기관 공유(cross-company) 리소스 — company 스코프 없음

CREATE TABLE plaza_posts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    board VARCHAR(20) NOT NULL,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    author_id VARCHAR(255) NOT NULL,
    author_name VARCHAR(100) NOT NULL,
    company_name VARCHAR(100),
    is_anonymous BOOLEAN NOT NULL DEFAULT FALSE,
    is_pinned BOOLEAN NOT NULL DEFAULT FALSE,
    is_hidden BOOLEAN NOT NULL DEFAULT FALSE,
    view_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP,
    modified_at TIMESTAMP,
    KEY idx_plaza_posts_board_created (board, created_at),
    KEY idx_plaza_posts_created (created_at)
);

CREATE TABLE plaza_comments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    post_id BIGINT NOT NULL,
    parent_id BIGINT NULL,
    author_id VARCHAR(255) NOT NULL,
    author_name VARCHAR(100) NOT NULL,
    company_name VARCHAR(100),
    is_anonymous BOOLEAN NOT NULL DEFAULT FALSE,
    is_accepted BOOLEAN NOT NULL DEFAULT FALSE,
    content TEXT NOT NULL,
    created_at TIMESTAMP,
    modified_at TIMESTAMP,
    KEY idx_plaza_comments_post (post_id),
    CONSTRAINT fk_plaza_comments_post FOREIGN KEY (post_id) REFERENCES plaza_posts(id) ON DELETE CASCADE
);

CREATE TABLE plaza_post_likes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    post_id BIGINT NOT NULL,
    user_id VARCHAR(255) NOT NULL,
    created_at TIMESTAMP,
    modified_at TIMESTAMP,
    UNIQUE KEY uk_plaza_like (post_id, user_id),
    CONSTRAINT fk_plaza_likes_post FOREIGN KEY (post_id) REFERENCES plaza_posts(id) ON DELETE CASCADE
);

CREATE TABLE plaza_post_views (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    post_id BIGINT NOT NULL,
    user_id VARCHAR(255) NOT NULL,
    created_at TIMESTAMP,
    modified_at TIMESTAMP,
    UNIQUE KEY uk_plaza_view (post_id, user_id),
    CONSTRAINT fk_plaza_views_post FOREIGN KEY (post_id) REFERENCES plaza_posts(id) ON DELETE CASCADE
);

CREATE TABLE plaza_reports (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    target_type VARCHAR(20) NOT NULL,
    target_id BIGINT NOT NULL,
    reporter_id VARCHAR(255) NOT NULL,
    reason VARCHAR(50) NOT NULL,
    created_at TIMESTAMP,
    modified_at TIMESTAMP,
    UNIQUE KEY uk_plaza_report (target_type, target_id, reporter_id)
);

CREATE TABLE plaza_library_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    category VARCHAR(20) NOT NULL,
    title VARCHAR(200) NOT NULL,
    description VARCHAR(1000),
    file_name VARCHAR(300) NOT NULL,
    file_size BIGINT NOT NULL,
    file_path VARCHAR(500) NOT NULL,
    uploader_id VARCHAR(255) NOT NULL,
    uploader_name VARCHAR(100) NOT NULL,
    company_name VARCHAR(100),
    download_count INT NOT NULL DEFAULT 0,
    is_hidden BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP,
    modified_at TIMESTAMP,
    KEY idx_plaza_library_category (category, created_at)
);

-- 운영 안내 글 시드
INSERT INTO plaza_posts (board, title, content, author_id, author_name, company_name, is_anonymous, is_pinned, is_hidden, view_count, created_at, modified_at)
VALUES (
    'FREE',
    '[운영] 케어브이 광장 이용 안내',
    '케어브이 광장이 열렸습니다!\n\n· 실무 Q&A: 업무 중 궁금한 점을 묻고 답해주세요. 질문자는 도움이 된 답변을 채택할 수 있습니다.\n· 평가 후기: 기관 평가 경험과 준비 노하우를 나눠주세요.\n· 자유: 현장 이야기를 자유롭게 나누는 공간입니다.\n\n광고·비방·개인정보 노출 게시물은 신고가 누적되면 자동으로 숨김 처리됩니다. 건강한 커뮤니티를 함께 만들어주세요.',
    'carev-admin',
    '케어브이 운영팀',
    '케어브이',
    FALSE, TRUE, FALSE, 0, NOW(), NOW()
);
