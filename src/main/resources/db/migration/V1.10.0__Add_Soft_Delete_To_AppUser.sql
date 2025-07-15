-- AppUser 테이블에 소프트 삭제를 위한 deleted_at 컬럼 추가
ALTER TABLE app_user
ADD COLUMN deleted_at DATETIME NULL;

-- email 컬럼의 unique 제약 조건 제거
-- 먼저 제약 조건 이름 확인 후 삭제
ALTER TABLE app_user
DROP INDEX uk_1j9d9a06i600gd43uu3km82jw;

-- 기존 email 인덱스는 검색 성능을 위해 유지
-- idx_app_user_email 인덱스가 이미 존재하므로 추가 작업 불필요

-- 복합 유니크 인덱스 생성 (email + deleted_at)
-- NULL 값은 MySQL에서 서로 다른 값으로 취급되므로 
-- 삭제되지 않은 사용자(deleted_at = NULL) 간에만 이메일 중복 방지
CREATE UNIQUE INDEX uk_app_user_email_deleted 
ON app_user(email, deleted_at);

-- deleted_at 컬럼에 일반 인덱스 추가 (조회 성능 향상)
CREATE INDEX idx_app_user_deleted_at 
ON app_user(deleted_at);