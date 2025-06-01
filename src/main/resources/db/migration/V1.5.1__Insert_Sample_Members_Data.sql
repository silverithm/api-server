-- 관리자 계정 생성
INSERT INTO members (username, password, name, email, phone_number, role, status, department, position, fcm_token) VALUES 
('admin', 'admin123', '관리자', 'admin@example.com', '010-0000-0000', 'ADMIN', 'ACTIVE', '관리부', '시스템 관리자', 'admin-fcm-token-1');

-- 샘플 직원들 생성
INSERT INTO members (username, password, name, email, phone_number, role, status, department, position, fcm_token) VALUES 
('caregiver1', 'password123', '김요양', 'caregiver1@example.com', '010-1111-1111', 'CAREGIVER', 'ACTIVE', '요양부', '요양보호사', 'caregiver1-fcm-token'),
('caregiver2', 'password123', '이보호', 'caregiver2@example.com', '010-1111-2222', 'CAREGIVER', 'ACTIVE', '요양부', '요양보호사', 'caregiver2-fcm-token'),
('office1', 'password123', '박사무', 'office1@example.com', '010-2222-1111', 'OFFICE', 'ACTIVE', '사무부', '사무원', 'office1-fcm-token'),
('office2', 'password123', '최업무', 'office2@example.com', '010-2222-2222', 'OFFICE', 'ACTIVE', '사무부', '주임', 'office2-fcm-token');

-- 샘플 가입 요청들 생성 (대기중, 승인됨, 거부됨 상태 각각)
INSERT INTO member_join_requests (username, password, name, email, phone_number, requested_role, department, position, fcm_token, status) VALUES 
('newuser1', 'password123', '신규사용자1', 'newuser1@example.com', '010-3333-1111', 'CAREGIVER', '요양부', '요양보호사', 'newuser1-fcm-token', 'PENDING'),
('newuser2', 'password123', '신규사용자2', 'newuser2@example.com', '010-3333-2222', 'OFFICE', '사무부', '사무원', 'newuser2-fcm-token', 'PENDING'),
('approved_user', 'password123', '승인된사용자', 'approved@example.com', '010-4444-1111', 'USER', '일반부', '일반직', 'approved-fcm-token', 'APPROVED'),
('rejected_user', 'password123', '거부된사용자', 'rejected@example.com', '010-5555-1111', 'USER', '일반부', '일반직', 'rejected-fcm-token', 'REJECTED');

-- 거부된 요청에 거부 사유 추가
UPDATE member_join_requests 
SET reject_reason = '필요 서류가 부족합니다', 
    approved_by = 1, 
    processed_at = NOW() 
WHERE username = 'rejected_user';

-- 승인된 요청에 승인 정보 추가
UPDATE member_join_requests 
SET approved_by = 1, 
    processed_at = DATE_SUB(NOW(), INTERVAL 1 DAY)
WHERE username = 'approved_user'; 