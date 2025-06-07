-- V1.7.1: Update sample vacation data with company associations

-- 기존 데이터 삭제 (회사 정보 없이 생성된 데이터)
DELETE FROM vacation_requests;
DELETE FROM vacation_limits;

-- 회사별 샘플 휴가 신청 데이터 추가
INSERT INTO vacation_requests (user_name, date, status, role, reason, user_id, password, type, company_id) VALUES
-- 첫 번째 회사 데이터
('김철수', '2024-01-15', 'APPROVED', 'CAREGIVER', '개인 사정', 'user_001', 'password123', 'regular', 1),
('이영희', '2024-01-15', 'APPROVED', 'CAREGIVER', '병원 진료', 'user_002', 'password123', 'regular', 1),
('박민수', '2024-01-16', 'PENDING', 'OFFICE', '연차 사용', 'user_003', 'password123', 'regular', 1),
('최지현', '2024-01-20', 'REJECTED', 'CAREGIVER', '가족 행사', 'user_004', 'password123', 'regular', 1),
('홍길동', '2024-01-22', 'APPROVED', 'CAREGIVER', '개인 휴가', 'user_005', 'password123', 'regular', 1),

-- 두 번째 회사 데이터 (회사 ID가 2라고 가정)
('김미영', '2024-01-25', 'PENDING', 'OFFICE', '연차', 'user_006', 'password123', 'regular', IFNULL((SELECT id FROM company WHERE id = 2), 1)),
('이준호', '2024-02-01', 'APPROVED', 'CAREGIVER', '병가', 'user_007', 'password123', 'regular', IFNULL((SELECT id FROM company WHERE id = 2), 1)),
('박서연', '2024-02-03', 'APPROVED', 'CAREGIVER', '개인 사정', 'user_008', 'password123', 'regular', IFNULL((SELECT id FROM company WHERE id = 2), 1)),
('김태현', '2024-02-05', 'PENDING', 'OFFICE', '휴가', 'user_009', 'password123', 'regular', IFNULL((SELECT id FROM company WHERE id = 2), 1)),
('이소미', '2024-02-10', 'APPROVED', 'CAREGIVER', '연차 사용', 'user_010', 'password123', 'regular', IFNULL((SELECT id FROM company WHERE id = 2), 1));

-- 회사별 샘플 휴가 제한 데이터 추가
INSERT INTO vacation_limits (date, max_people, role, company_id) VALUES
-- 첫 번째 회사 제한
('2024-01-15', 2, 'CAREGIVER', 1),
('2024-01-16', 1, 'OFFICE', 1),
('2024-01-20', 3, 'CAREGIVER', 1),
('2024-01-22', 2, 'CAREGIVER', 1),
('2024-02-01', 3, 'CAREGIVER', 1),

-- 두 번째 회사 제한 (회사가 존재하는 경우에만)
('2024-01-25', 1, 'OFFICE', IFNULL((SELECT id FROM company WHERE id = 2), 1)),
('2024-02-03', 2, 'CAREGIVER', IFNULL((SELECT id FROM company WHERE id = 2), 1)),
('2024-02-05', 1, 'OFFICE', IFNULL((SELECT id FROM company WHERE id = 2), 1)),
('2024-02-10', 3, 'CAREGIVER', IFNULL((SELECT id FROM company WHERE id = 2), 1)),
('2024-02-15', 2, 'CAREGIVER', IFNULL((SELECT id FROM company WHERE id = 2), 1)); 