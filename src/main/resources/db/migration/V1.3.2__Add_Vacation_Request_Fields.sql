-- VacationRequest 테이블에 새로운 필드 추가
ALTER TABLE vacation_requests 
ADD COLUMN user_id VARCHAR(100) COMMENT '사용자 ID',
ADD COLUMN password VARCHAR(255) NOT NULL COMMENT '삭제용 비밀번호',
ADD COLUMN type VARCHAR(50) DEFAULT 'regular' COMMENT '휴가 유형';

-- 인덱스 추가
CREATE INDEX idx_user_id ON vacation_requests(user_id);
CREATE INDEX idx_type ON vacation_requests(type); 