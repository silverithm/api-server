-- 샘플 휴가 신청 데이터
INSERT INTO vacation_requests (user_name, date, status, role, reason) VALUES
('김철수', '2024-01-15', 'APPROVED', 'CAREGIVER', '개인 사정'),
('이영희', '2024-01-15', 'APPROVED', 'CAREGIVER', '병원 진료'),
('박민수', '2024-01-16', 'PENDING', 'OFFICE', '연차 사용'),
('최지현', '2024-01-20', 'REJECTED', 'CAREGIVER', '가족 행사'),
('홍길동', '2024-01-22', 'APPROVED', 'CAREGIVER', '개인 휴가'),
('김미영', '2024-01-25', 'PENDING', 'OFFICE', '연차'),
('이준호', '2024-02-01', 'APPROVED', 'CAREGIVER', '병가'),
('박서연', '2024-02-03', 'APPROVED', 'CAREGIVER', '개인 사정'),
('김태현', '2024-02-05', 'PENDING', 'OFFICE', '휴가'),
('이소미', '2024-02-10', 'APPROVED', 'CAREGIVER', '연차 사용');

-- 샘플 휴가 제한 데이터
INSERT INTO vacation_limits (date, max_people, role) VALUES
('2024-01-15', 2, 'CAREGIVER'),
('2024-01-16', 1, 'OFFICE'),
('2024-01-20', 3, 'CAREGIVER'),
('2024-01-22', 2, 'CAREGIVER'),
('2024-01-25', 1, 'OFFICE'),
('2024-02-01', 3, 'CAREGIVER'),
('2024-02-03', 2, 'CAREGIVER'),
('2024-02-05', 1, 'OFFICE'),
('2024-02-10', 3, 'CAREGIVER'),
('2024-02-15', 2, 'CAREGIVER'); 