-- 대체휴무 지원: 연차를 사용하지 않는 휴무의 세부 유형을 저장한다.
-- 기존에는 클라이언트가 vacationType을 보내도 서버가 버려서 병가/긴급 등의 정보가 유실됐다.
-- 휴무 종류 자체(regular / mandatory / substitute)는 기존 type 컬럼에 그대로 저장된다.
ALTER TABLE vacation_requests
    ADD COLUMN vacation_type VARCHAR(50) NULL
    COMMENT '연차 미사용 휴무의 세부 유형: personal, sick, emergency, family, other, substitute';

-- 대체휴무 집계/조회용 인덱스
CREATE INDEX idx_vacation_type ON vacation_requests(vacation_type);
