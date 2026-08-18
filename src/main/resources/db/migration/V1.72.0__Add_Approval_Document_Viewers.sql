-- 전자결재 문서 열람 대상 지정.
--
-- 지금까지 결재 문서는 기관 관리자만 전체를 볼 수 있었고, 직원은 자기가 기안한 것만 볼 수 있었다.
-- 회의록처럼 "특정 직책(예: 사회복지사) 전원이 같이 봐야 하는" 문서를 공유할 방법이 없어
-- 양식과 문서 각각에 열람 대상을 지정할 수 있게 한다.
--
-- 지정 단위는 직책(POSITION) 또는 개인(MEMBER/ADMIN)이며, 결재선(approval_steps)과 같은
-- (type, ref_id) + 이름 스냅샷 구조를 따른다. 이름을 함께 저장해두면 직책명·사람 이름으로
-- 문서를 검색할 때 조인 없이 걸러낼 수 있고, 나중에 직책이 사라져도 문서에 남은 기록은 유지된다.
--
-- 관리자, 기안자 본인, 결재선에 이름이 오른 사람은 여기에 지정하지 않아도 항상 열람 가능하다.

-- 양식별 기본 열람 대상 — 이 양식으로 기안하면 문서 쪽으로 복사된다
CREATE TABLE approval_template_viewers (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    template_id BIGINT NOT NULL COMMENT '양식 ID',
    viewer_type VARCHAR(10) NOT NULL COMMENT '지정 단위 (POSITION=직책, MEMBER=직원 개인, ADMIN=관리자 개인)',
    ref_id BIGINT NOT NULL COMMENT '대상 PK (positions.id / members.id / app_user.id)',
    viewer_name VARCHAR(255) NOT NULL COMMENT '대상 이름 (지정 시점 스냅샷)',
    created_at DATETIME NOT NULL,
    CONSTRAINT fk_approval_template_viewers_template FOREIGN KEY (template_id) REFERENCES approval_templates(id) ON DELETE CASCADE,
    CONSTRAINT uk_approval_template_viewers UNIQUE (template_id, viewer_type, ref_id),
    INDEX idx_approval_template_viewers_template (template_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='전자결재 양식별 기본 열람 대상';

-- 문서별 열람 대상 — 기안 시 양식 기본값으로 채워지고 기안자가 더하거나 뺄 수 있다
CREATE TABLE approval_request_viewers (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    approval_request_id BIGINT NOT NULL COMMENT '결재 요청 ID',
    viewer_type VARCHAR(10) NOT NULL COMMENT '지정 단위 (POSITION=직책, MEMBER=직원 개인, ADMIN=관리자 개인)',
    ref_id BIGINT NOT NULL COMMENT '대상 PK (positions.id / members.id / app_user.id)',
    viewer_name VARCHAR(255) NOT NULL COMMENT '대상 이름 (지정 시점 스냅샷)',
    created_at DATETIME NOT NULL,
    CONSTRAINT fk_approval_request_viewers_request FOREIGN KEY (approval_request_id) REFERENCES approval_requests(id) ON DELETE CASCADE,
    CONSTRAINT uk_approval_request_viewers UNIQUE (approval_request_id, viewer_type, ref_id),
    INDEX idx_approval_request_viewers_request (approval_request_id),
    -- "내가 볼 수 있는 문서" 조회가 이 인덱스를 탄다
    INDEX idx_approval_request_viewers_lookup (viewer_type, ref_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='전자결재 문서별 열람 대상';
