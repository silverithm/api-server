-- V1.89.0: schedules.manager_type을 VARCHAR에서 ENUM으로 바꾼다
--
-- V1.88.0에서 VARCHAR(20)으로 만들었는데, Hibernate 6는 @Enumerated(STRING)인
-- Java enum을 MySQL 네이티브 ENUM으로 매핑한다. 운영은 ddl-auto=validate라
-- 타입이 어긋나면 기동 자체가 실패한다(실제로 배포가 여기서 막혔다).
--
-- 이 스키마의 다른 enum 컬럼(예: schedules.category)도 전부 ENUM 타입이므로
-- 관례에 맞춘다. 값은 그대로 'MEMBER'/'ADMIN'이라 데이터 변환은 필요 없다.

ALTER TABLE schedules
    MODIFY COLUMN manager_type ENUM('MEMBER', 'ADMIN') NOT NULL DEFAULT 'MEMBER';
