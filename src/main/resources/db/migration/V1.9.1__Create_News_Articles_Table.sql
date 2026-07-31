CREATE TABLE news_articles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(500) NOT NULL,
    source VARCHAR(100),
    category VARCHAR(20) NOT NULL,
    link VARCHAR(768) NOT NULL,
    published_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP,
    modified_at TIMESTAMP,
    UNIQUE KEY uk_news_articles_link (link),
    KEY idx_news_articles_published_at (published_at),
    KEY idx_news_articles_category_published_at (category, published_at)
);
