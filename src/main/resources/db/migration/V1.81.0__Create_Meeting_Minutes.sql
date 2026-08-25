-- 회의록: 작성하고, 참석자에게 알리고, 서명을 모은다.
--
-- 케어포에도 회의 작성은 있지만 서명을 받을 방법이 없다. 요양기관 회의록은
-- "참석자 전원이 내용을 확인했다"가 핵심이라, 참석자별 서명을 병렬로 모으는
-- 별도 도메인으로 만든다. 결재선(approval_steps)은 순차 진행 모델이라 맞지 않고,
-- 완료된 회의록은 이관 문서(V1.79)와 같은 방식으로 결재함에 완결 문서로 들어간다.
--
-- 녹음은 60초 조각으로 잘라 올리고(브라우저가 죽어도 그 앞까지는 남는다),
-- 실시간 전사문은 transcript에 주기 저장된다. 원문(raw_notes)은 AI 정리 후에도
-- 지우지 않는다 — 잘못 정리되면 되돌릴 원본이 필요하다.

-- 기관별 양식(섹션 구성). 행이 없으면 애플리케이션 기본값을 쓴다
-- (schedule_category_settings와 같은 널-폴백 방식 — 기관별 시드가 필요 없다).
CREATE TABLE meeting_minutes_templates (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    sections JSON NOT NULL COMMENT '[{"key","label"}] 섹션 구성',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uq_meeting_minutes_templates_company (company_id),
    CONSTRAINT fk_mm_templates_company FOREIGN KEY (company_id) REFERENCES company (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE meeting_minutes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL COMMENT '주제',
    location VARCHAR(255) NULL COMMENT '회의 장소',
    author_type VARCHAR(10) NOT NULL COMMENT 'ADMIN(AppUser) | MEMBER',
    author_ref_id BIGINT NOT NULL,
    author_name VARCHAR(255) NOT NULL COMMENT '작성 시점 이름 스냅샷',
    meeting_start_at DATETIME NOT NULL,
    meeting_end_at DATETIME NULL,
    -- IN_PROGRESS: 회의 진행/작성 중 (녹음·전사가 붙는다, 참석자에게 아직 안 알림)
    -- REGISTERED: 등록됨 (참석자 알림 발송, 서명 수집 중)
    -- COMPLETED: 완료 (전자결재 완결 문서로 등록됨)
    status VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS',
    sections_json JSON NULL COMMENT '[{"key","label","content"}] — 양식 스냅샷 + 정리된 내용',
    raw_notes LONGTEXT NULL COMMENT '타이핑 원문 — AI 정리 후에도 보존',
    transcript LONGTEXT NULL COMMENT '실시간 전사문 누적 (주기 저장)',
    approval_request_id BIGINT NULL COMMENT '완료 시 만들어지는 결재함 문서',
    completed_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_meeting_minutes_company_status (company_id, status),
    KEY idx_meeting_minutes_company_start (company_id, meeting_start_at),
    CONSTRAINT fk_meeting_minutes_company FOREIGN KEY (company_id) REFERENCES company (id),
    CONSTRAINT fk_meeting_minutes_approval FOREIGN KEY (approval_request_id) REFERENCES approval_requests (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 참석자. 서명은 결재선처럼 순차가 아니라 각자 병렬로 한다.
-- EXTERNAL은 계정이 없는 외부 참석자 — ref_id 없이 이름만 남고,
-- 서명은 관리자 화면에서 입회 서명(현장 그리기)으로 받는다.
CREATE TABLE meeting_minutes_attendees (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    meeting_minutes_id BIGINT NOT NULL,
    attendee_type VARCHAR(10) NOT NULL COMMENT 'ADMIN | MEMBER | EXTERNAL',
    ref_id BIGINT NULL COMMENT 'EXTERNAL이면 NULL',
    attendee_name VARCHAR(255) NOT NULL COMMENT '지정 시점 이름 스냅샷',
    signature_url VARCHAR(1000) NULL,
    signed_at DATETIME NULL,
    notified_at DATETIME NULL,
    reminded_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_mm_attendees_minutes (meeting_minutes_id),
    CONSTRAINT fk_mm_attendees_minutes FOREIGN KEY (meeting_minutes_id)
        REFERENCES meeting_minutes (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 녹음 원본 조각 (60초 단위). 통파일 업로드도 조각 1개로 들어온다.
CREATE TABLE meeting_minutes_audio_chunks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    meeting_minutes_id BIGINT NOT NULL,
    seq INT NOT NULL,
    file_url VARCHAR(1000) NOT NULL,
    duration_sec INT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_mm_chunks_minutes (meeting_minutes_id, seq),
    CONSTRAINT fk_mm_chunks_minutes FOREIGN KEY (meeting_minutes_id)
        REFERENCES meeting_minutes (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 자료 첨부 (approval_request_attachments와 같은 모양)
CREATE TABLE meeting_minutes_attachments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    meeting_minutes_id BIGINT NOT NULL,
    file_url VARCHAR(1000) NOT NULL,
    file_name VARCHAR(500) NOT NULL,
    file_size BIGINT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_mm_attachments_minutes (meeting_minutes_id),
    CONSTRAINT fk_mm_attachments_minutes FOREIGN KEY (meeting_minutes_id)
        REFERENCES meeting_minutes (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 알림 타입에 MEETING_MINUTES 추가.
-- CHAT·APPROVAL은 Java enum에는 있었지만 이 ENUM 컬럼에는 빠져 있어서
-- 해당 타입의 알림 이력 저장이 실패하고 있었다 — 이번에 전체 목록으로 맞춘다.
ALTER TABLE notifications
MODIFY COLUMN type ENUM(
    'VACATION_APPROVED',
    'VACATION_REJECTED',
    'VACATION_SUBMITTED',
    'VACATION_REMINDER',
    'MEMBER_JOIN_REQUESTED',
    'MEMBER_JOIN_APPROVED',
    'MEMBER_JOIN_REJECTED',
    'NOTICE',
    'CHAT',
    'APPROVAL',
    'MEETING_MINUTES',
    'GENERAL'
) NOT NULL DEFAULT 'GENERAL' COMMENT '알림 타입';
