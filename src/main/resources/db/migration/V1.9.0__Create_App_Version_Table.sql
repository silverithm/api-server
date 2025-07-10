CREATE TABLE app_version (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ios_version VARCHAR(255) NOT NULL,
    ios_minimum_version VARCHAR(255) NOT NULL,
    android_version VARCHAR(255) NOT NULL,
    android_minimum_version VARCHAR(255) NOT NULL,
    update_message TEXT,
    force_update BOOLEAN NOT NULL,
    created_at TIMESTAMP,
    modified_at TIMESTAMP
);