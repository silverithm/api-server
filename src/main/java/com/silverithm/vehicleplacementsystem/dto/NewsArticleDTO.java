package com.silverithm.vehicleplacementsystem.dto;

import com.silverithm.vehicleplacementsystem.entity.NewsArticle;
import java.time.LocalDateTime;

public record NewsArticleDTO(
        Long id,
        String title,
        String source,
        String category,
        String url,
        LocalDateTime publishedAt
) {
    public static NewsArticleDTO from(NewsArticle article) {
        return new NewsArticleDTO(
                article.getId(),
                article.getTitle(),
                article.getSource(),
                article.getCategory().getKey(),
                article.getLink(),
                article.getPublishedAt()
        );
    }
}
