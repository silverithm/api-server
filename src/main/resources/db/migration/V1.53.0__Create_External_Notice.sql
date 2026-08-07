CREATE TABLE external_notice (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    source VARCHAR(30) NOT NULL,
    external_id VARCHAR(30) NOT NULL,
    title VARCHAR(500) NOT NULL,
    url VARCHAR(700) NOT NULL,
    posted_date DATE,
    created_at DATETIME,
    UNIQUE KEY uk_external_notice (source, external_id),
    KEY idx_external_notice_posted_date (posted_date)
);
