-- 고충·신고 + 건의함 (VoiceBox)
-- 직원이 기관에 남기는 목소리. 고충·신고(GRIEVANCE)는 익명 제출 가능하며
-- 열람은 기관 관리자만 가능하다 (작성자 정보는 익명일 때 응답에서 가려진다).
CREATE TABLE voice_messages (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    type VARCHAR(20) NOT NULL COMMENT 'GRIEVANCE(고충·신고) | SUGGESTION(건의)',
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    is_anonymous BOOLEAN NOT NULL DEFAULT FALSE COMMENT '익명 제출 여부 — 익명이면 관리자 응답에서 작성자 정보를 가린다',
    author_type VARCHAR(10) NOT NULL COMMENT 'ADMIN(AppUser) | MEMBER(Member) — 내 내역 조회용, 익명이어도 저장',
    author_ref_id BIGINT NOT NULL COMMENT 'app_user.id 또는 members.id',
    author_name VARCHAR(100) NOT NULL COMMENT '작성 시점 이름 스냅샷 (익명이면 응답에서 미노출)',
    status VARCHAR(20) NOT NULL DEFAULT 'RECEIVED' COMMENT 'RECEIVED | IN_REVIEW | RESOLVED | ON_HOLD',
    admin_reply TEXT NULL,
    replied_at DATETIME NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    CONSTRAINT fk_voice_messages_company FOREIGN KEY (company_id) REFERENCES company(id),
    INDEX idx_voice_company_type_time (company_id, type, created_at),
    INDEX idx_voice_author (author_type, author_ref_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
